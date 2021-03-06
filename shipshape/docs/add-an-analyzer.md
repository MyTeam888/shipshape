<!--
// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
-->
# Deploy an analyzer using Shipshape

## Dependencies

Let's first make sure you're set up with the necessary tools to build and
deploy a shipshape analyzer. You will need:

* Docker
* The Shipshape CLI
* The Shipshape API (currently supporting go and Java)
* Whatever language tools and dependencies your analyzer needs to build and run

You'll need to have docker and the CLI already installed on [a Linux
machine](https://github.com/google/shipshape/blob/master/shipshape/docs/linux-setup.md).

For this tutorial, we be creating an analyzer implemented in Go.
If you do not already have it, install go by following the
[go install instructions](https://golang.org/doc/install).

## Implement your analyzer

Creating an analyzer involves making three things:

1. Creating an analyzer. We recommend implementing our provided API, but this can be done a language of your choice.
2. A service that exposes the analyzer as a service. The required API is defined by
   [shipshape_rpc.proto](https://github.com/google/shipshape/blob/master/shipshape/proto/shipshape_rpc.proto).  If you utilize the provided library and implemented the provided API in step 1, we implement the hard parts for you.
3. A docker image that starts the service and exposes it on port 10005.

### Go setup
First, we need to make sure go is all set up. Create gocode/src/helloworld, and
set your go path.

    mkdir -p gocode/src/helloworld
    export GOPATH=/home/$USER/gocode

Get shipshape's go API

    go get github.com/google/shipshape/shipshape/api

Create two packages, one for your analyzer and one for your service.

    cd gocode/src
    mkdir helloworld/myanalyzer
    mkdir helloworld/myservice

### Create an analyzer
We'll do this by implementing
[api.Analyzer](https://github.com/google/shipshape/blob/master/shipshape/api/analyzer.go).
The
[AndroidLint analyzer](https://github.com/google/shipshape/blob/master/shipshape/androidlint_analyzer/androidlint/analyzer.go)
is a helpful example

First, implement `Category()`. This is the name of the analyzer, and all results
returned from this analyzer should use this as the name.

helloworld/myanalyzer/analyzer.go
```
package myanalyzer

import (
  "github.com/golang/protobuf/proto"

  notepb "github.com/google/shipshape/shipshape/proto/note_proto"
  ctxpb "github.com/google/shipshape/shipshape/proto/shipshape_context_proto"
  trpb "github.com/google/shipshape/shipshape/proto/textrange_proto"
)

type Analyzer struct{}

func (Analyzer) Category() string { return "HelloWorld" }

```

Implement a simple `Analyze` method that returns a single note
```
func (a Analyzer) Analyze(ctx *ctxpb.ShipshapeContext) ([]*notepb.Note, error) {
  return []*notepb.Note{
    &notepb.Note{
      Category:    proto.String(a.Category()),
      Subcategory: proto.String("greetings"),
      Description: proto.String("Hello world, this is a code note"),
      Location: &notepb.Location{
        SourceContext: ctx.SourceContext,
      },
    },
  }, nil
}
```

When this is method is called, it will be provided with a [`ShipshapeContext`](https://github.com/google/shipshape/blob/master/shipshape/proto/shipshape_context.proto),
which is a protocol message that represents a request to have some code
analyzed. It contains useful information about the code being analyzed and any
information about the context it is running in.

Your analysis should produce a list of
[`Note`s](https://github.com/google/shipshape/blob/master/shipshape/proto/note.proto),
which is another protocol message. A note represents a single piece of
information from an analysis tool. It can be associated with a line of code in a
file, and it can provide suggestions for how to fix the error.

Let's modify our Analyze method to now produce one note for every file, and
place it on the first line of the file.
```
func (a Analyzer) Analyze(ctx *ctxpb.ShipshapeContext) ([]*notepb.Note, error) {
  notes := []*notepb.Note{}
  for _, path := range ctx.FilePath {
    notes = append(notes,
      &notepb.Note{
        Category:    proto.String(a.Category()),
        Subcategory: proto.String("greetings"),
        Description: proto.String("Hello world, this is a code note"),
        Location: &notepb.Location{
          SourceContext: ctx.SourceContext,
          Path: proto.String(path),
          Range: &trpb.TextRange{
            StartLine: proto.Int(1),
          },
        },
      })
  }
  return notes, nil
}
```

### Implement a server for your analyzer
Now, we just need to implement a service that runs on port 10005 and calls to
your analyzer. You can use api.Service to help with this.  As an example, see
the
[AndroidLint service](https://github.com/google/shipshape/blob/master/shipshape/androidlint_analyzer/androidlint/service.go)

helloworld/myservice/service.go
```
package main

import (
  "log"
  "net/http"

  "helloworld/myanalyzer"
  "github.com/google/shipshape/shipshape/api"
  "github.com/google/shipshape/shipshape/util/rpc/server"

  ctxpb "github.com/google/shipshape/shipshape/proto/shipshape_context_proto"
)

func main() {
  // The shipshape service will connect to an AnalyzerService
  // at port 10005 in the container. (The service will map this to a different
  // external port at startup so that it doesn't clash with other analyzers.)
  s := server.Service{Name: "AnalyzerService"}
  addr := ":10005"

  // Make a new analyzer service. This runs at the "PRE_BUILD" stage, but you
  // can also create analyzer that require build outputs.
  as := api.CreateAnalyzerService([]api.Analyzer{new(myanalyzer.Analyzer)},
      ctxpb.Stage_PRE_BUILD)
  if err := s.Register(as); err != nil {
    log.Fatalf("Registering analyzer service failed: %v", err)
  }

  log.Printf("-- Starting server endpoint at %q\n", addr)
  http.Handle("/", server.Endpoint{&s})
  if err := http.ListenAndServe(addr, nil); err != nil {
    log.Fatalf("Server startup failed: %v", err)
  }
}
```

Make sure your analyzer builds

    go build helloworld/myanalyzer
    go build helloworld/myservice


### Java
Java instructions will be available soon.

## Create a Docker file
Shipshape will start and run your service using [Docker](http://docker.io).
You'll need to provide a docker file that creates a docker image. A docker
image is similar to a VM image; it contains your analyzer and all the
dependencies needed to run it. (Unlike a traditional virtual machine though,
[a container will share the OS to save space](https://www.docker.com/whatisdocker).)
As an example, the
[AndroidLint analyzer provides a docker file with all its dependencies](https://github.com/google/shipshape/blob/master/shipshape/androidlint_analyzer/docker/Dockerfile)

Your Dockerfile will also need to actually start up your service through an
endpoint script, which is just a small shell script that starts your service.
AndroidLint provides an example of
[starting the service](https://github.com/google/shipshape/blob/master/shipshape/androidlint_analyzer/docker/endpoint.sh)

helloworld/Dockerfile
```
FROM debian:wheezy

# Make sure all package lists are up-to-date
RUN apt-get update && apt-get upgrade -y && apt-get clean

# Install any dependencies that you need here

# Set up the analyzer
# Add the binary that we'll run in the endpoint script
# and the endpoint script itself.
COPY myservice /myservice
COPY helloworld/endpoint.sh /endpoint.sh

# 10005 is the port that the shipshape service will expect to see a Shipshape
# Analyzer at.
EXPOSE 10005

# Start the endpoint script.
ENTRYPOINT ["/endpoint.sh"]
```

helloworld/endpoint.sh
```
#!/bin/bash

# Shipshape will map the /shipshape-output directory to /tmp on the local
# machine, which is where you can find your logs
./myservice &> /shipshape-output/myanalyzer.log
```

Make sure to make the script executable!

    chmod 755 helloworld/endpoint.sh

##  Test your analyzer locally

Build a docker image with the tag "local", using the file we created earlier.
Notice that we're building it from the directory with our myservice binary.

    docker build --tag=myanalyzer:local --file=helloworld/Dockerfile .

Run the local analyzer. When you use the tag `local`, shipshape won't attempt to
pull it from a remote location, but will use your locally built image.

    shipshape --analyzer_images=myanalyzer:local <directory>

## Make your analyzer publicly accessable

Push it up to gcr.io or docker.io, so that others can access it

    docker tag myanalyzer:local [REGISTRYHOST/][USERNAME/]NAME[:TAG]
    docker push [SAME_NAME_AND_TAG_AS_ABOVE]

Now you can access the public version of your analyzer

   shipshape --analyzer_image=[SAME_NAME_AND_TAG_AS_ABOVE] directory

Add it to [our list of
analyzers](https://github.com/google/shipshape/blob/master/README.md#contributed-analyzers) by sending us a pull request!
