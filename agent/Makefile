VERSION ?= $(shell git describe --tags --always --dirty 2>/dev/null || echo "dev")
LDFLAGS := -X main.Version=$(VERSION) -s -w

.PHONY: build build-linux build-windows pkg-deb pkg-rpm pkg-exe clean

build: build-linux build-windows

build-linux:
	mkdir -p dist
	GOOS=linux GOARCH=amd64 go build -ldflags "$(LDFLAGS)" -o dist/pulse-agent-linux-amd64 .

build-windows:
	mkdir -p dist
	GOOS=windows GOARCH=amd64 go build -ldflags "$(LDFLAGS)" -o dist/pulse-agent-windows-amd64.exe .

pkg-deb: build-linux
	VERSION=$(VERSION) nfpm pkg --config packaging/nfpm.yaml --packager deb --target dist/

pkg-rpm: build-linux
	VERSION=$(VERSION) nfpm pkg --config packaging/nfpm.yaml --packager rpm --target dist/

pkg-exe: build-windows
	makensis packaging/windows/installer.nsi

clean:
	rm -rf dist/
