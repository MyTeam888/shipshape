// Package httpencoding provides functions to transparently encode/decode HTTP bodies
package httpencoding

import (
	"compress/gzip"
	"compress/zlib"
	"fmt"
	"io"
	"net/http"
	"strings"
)

// CompressData returns a writer that writes encoded data to w. The chosen
// encoding is based on the Accept-Encoding header and defaults to the identity
// encoding.
func CompressData(w http.ResponseWriter, r *http.Request) io.WriteCloser {
	encodings := strings.Split(r.Header.Get("Accept-Encoding"), ",")
	for _, encoding := range encodings {
		switch encoding {
		case "gzip":
			w.Header().Set("Content-Encoding", "gzip")
			return gzip.NewWriter(w)
		case "deflate":
			w.Header().Set("Content-Encoding", "deflate")
			return zlib.NewWriter(w)
		case "identity":
			return noopCloser{w}
		}
	}
	return noopCloser{w}
}

// UncompressData returns a reads that decodes data from r.Body. The encoding is
// determined based on the Content-Encoding header and an error is returned if
// the encoding is unknown.
func UncompressData(r *http.Response) (io.ReadCloser, error) {
	encoding := r.Header.Get("Content-Encoding")
	var (
		cr  io.ReadCloser
		err error
	)
	switch encoding {
	case "gzip":
		cr, err = gzip.NewReader(r.Body)
	case "deflate":
		cr, err = zlib.NewReader(r.Body)
	case "identity":
	case "":
		return r.Body, nil
	default:
		return nil, fmt.Errorf("unknown encoding: %q", encoding)
	}
	if err != nil {
		return nil, err
	}
	return &decodedReader{r.Body, cr}, nil
}

// noopCloser is a io.WriteCloser with a no-op Close
type noopCloser struct {
	io.Writer
}

// Close implements Closer for noopClosers.
func (noopCloser) Close() error {
	return nil
}

type decodedReader struct {
	orig io.ReadCloser
	r    io.ReadCloser
}

func (r *decodedReader) Read(p []byte) (int, error) {
	return r.r.Read(p)
}

func (r *decodedReader) Close() error {
	if err := r.r.Close(); err != nil {
		return err
	}
	return r.orig.Close()
}
