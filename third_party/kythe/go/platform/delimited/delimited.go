// Package delimited implements a reader and writer for simple streams of
// length-delimited byte records.  Each record is written as a varint-encoded
// length in bytes, followed immediately by the record itself.
//
// A stream consists of a sequence of such records packed consecutively without
// additional padding.  There are no checksums or compression.
package delimited

import (
	"bufio"
	"encoding/binary"
	"fmt"
	"io"

	"code.google.com/p/goprotobuf/proto"
)

// A Reader consumes delimited records from an io.Reader.
//
// Usage:
//   rd := delimited.NewReader(r)
//   for {
//     rec, err := rd.Next()
//     if err == io.EOF {
//       break
//     } else if err != nil {
//       log.Fatal(err)
//     }
//     doStuffWith(rec)
//   }
//
type Reader struct {
	buf  *bufio.Reader
	data []byte
}

// Next returns the next length-delimited record from the input, or io.EOF if
// there are no more records available.  Returns io.ErrUnexpectedEOF if a short
// record is found, with a length of n but fewer than n bytes of data.  Because
// there is no resynchronization mechanism, it is generally not possible to
// recover from a short record in this format.
//
// The slice returned is valid only until a subsequent call to Next.
func (r *Reader) Next() ([]byte, error) {
	size, err := binary.ReadUvarint(r.buf)
	if err != nil {
		return nil, err
	}
	if cap(r.data) < int(size) {
		r.data = make([]byte, size)
	} else {
		r.data = r.data[:size]
	}

	if _, err := io.ReadFull(r.buf, r.data); err != nil {
		return nil, err
	}
	return r.data, nil
}

// NextProto reads a record using Next and decodes it into the given
// proto.Message.
func (r *Reader) NextProto(pb proto.Message) error {
	rec, err := r.Next()
	if err != nil {
		return err
	}
	return proto.Unmarshal(rec, pb)
}

// NewReader constructs a new delimited Reader for the records in r.
func NewReader(r io.Reader) *Reader {
	return &Reader{buf: bufio.NewReader(r)}
}

// A Writer outputs delimited records to an io.Writer.
//
// Basic usage:
//   wr := delimited.NewWriter(w)
//   for record := range records {
//      if err := wr.Put(record); err != nil {
//        log.Fatal(err)
//      }
//   }
//
type Writer struct {
	w io.Writer
}

// PutProto encodes and writes the specified proto.Message to the writer.
func (w *Writer) PutProto(msg proto.Message) error {
	rec, err := proto.Marshal(msg)
	if err != nil {
		return fmt.Errorf("error encoding proto: %v", err)
	}
	return w.Put(rec)
}

// Put writes the specified record to the writer.  It equivalent to Write,
// but discards the number of bytes written.
func (w *Writer) Put(record []byte) error {
	_, err := w.Write(record)
	return err
}

// Write writes the specified record to the underlying writer, returning the
// total number of bytes written including the length tag.  This method also
// satisfies io.Writer.
func (w *Writer) Write(record []byte) (int, error) {
	var buf [binary.MaxVarintLen64]byte
	v := binary.PutUvarint(buf[:], uint64(len(record)))

	nw, err := w.w.Write(buf[:v])
	if err != nil {
		return 0, err
	}
	dw, err := w.w.Write(record)
	if err != nil {
		return nw, err
	}
	return nw + dw, nil
}

// NewWriter constructs a new delimited Writer that writes records to w.
func NewWriter(w io.Writer) *Writer {
	return &Writer{w: w}
}
