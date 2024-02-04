PROJECT_NAME := rtc-sdk-rtmp-cdn-demo
PROJECT_VERSION ?= "0.1."$(shell date -u +'%Y%m%d%H')

.PHONY: build clean

build: clean
	@echo ">> build"
	@mkdir build && cd build && cmake ../src && make -j8
	@echo ">> done"

clean:
	@echo ">> clean"
	@rm -rf build
	@rm -rf out
	@echo ">> done"
