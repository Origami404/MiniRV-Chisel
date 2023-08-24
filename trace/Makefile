VSRC = $(wildcard vsrc/ram1.v mySoC/*) 
CSRC = $(wildcard golden_model/*.c) $(wildcard golden_model/stage/*.c) $(wildcard golden_model/peripheral/*.c) $(wildcard csrc/*.c csrc/*.cpp)
SIM_OPTS = --trace -Wno-lint -Wno-style -Wno-TIMESCALEMOD
TEST = sb
TESTFILE = meminit.bin
PWD = $(shell pwd)

build: $(VSRC) $(CSRC)
	@verilator -cc --exe --build $(VSRC) --top-module miniRV_SoC $(CSRC) $(SIM_OPTS) +define+PATH=$(TESTFILE) -CFLAGS -DPATH=$(TESTFILE) -ImySoC -CFLAGS -I$(PWD)/golden_model/include
	@mkdir -p waveform
run: build
	@ln -sf bin/$(TEST).bin $(TESTFILE)
	@./obj_dir/VminiRV_SoC $(TEST)
run_for_python:  # should run "make all" first, for python-based test
	@ln -sf bin/$(TEST).bin $(TESTFILE)
	@./obj_dir/VminiRV_SoC $(TEST)
	@rm -rf $(TESTFILE)
$(TESTFILE):
	ln -sf bin/$(TEST).bin $(TESTFILE)
clean:
	rm -rf obj_dir waveform $(TESTFILE)

.PHONY: run debug clean
