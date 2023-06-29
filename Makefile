# Source and target files/directories
project = $(shell grep projectName build.sc | cut -d= -f2|tr -d "\"" |xargs)
scala_files = $(wildcard $(project)/src/*.scala) $(wildcard $(project)/resources/*.scala) $(wildcard $(project)/test/src/*.scala)
generated_files = generated					# Destination directory for generated files

# Toolchains and tools
MILL = ./mill
DOCKERARGS  = run --rm -v $(PWD):/src -w /src

# Define utility applications for simulation
YOSYS = docker $(DOCKERARGS) hdlc/yosys yosys
VERILATOR_LOCAL := $(shell command -v verilator 2> /dev/null)
VERILATOR_ARGS = --name verilator --hostname verilator --rm -it --entrypoint= -v $(PWD):/work -w /work
ifndef VERILATORLOCAL
	VERILATOR = docker $(DOCKERARGS) $(VERILATOR_ARGS) gcr.io/hdl-containers/verilator:latest
else
	VERILATOR =
endif

# Default board PLL and parameters to be passed to Chisel (require parsing at Toplevel)
BOARD := bypass
# Chisel Params below are:
# --target:fpga -> https://github.com/chipsalliance/firrtl/blob/82da33135fcac1a81e8ea95f47626e80b4e80fd1/src/main/scala/firrtl/stage/FirrtlCompilerTargets.scala
# --emission-options=disableMemRandomization,disableRegisterRandomization -> https://github.com/chipsalliance/firrtl/pull/2396
CHISEL_PARAMS = --target:fpga --emission-options=disableMemRandomization,disableRegisterRandomization

# Targets
chisel: $(generated_files) ## Generates Verilog code from Chisel sources (output to ./generated)
$(generated_files): $(scala_files) build.sc Makefile
	@rm -rf $@
	@test "$(BOARD)" != "bypass" || (printf "Generating design with bypass PLL (for simulation). If required, set BOARD and PLLFREQ variables to one of the supported boards: .\n" ; test -f project.core && cat project.core|grep "\-board"|cut -d '-' -f 4 | grep -v bypass | sed s/board\ //g |tr -s '\n' ','| sed 's/,$$/\n/'; echo "Eg. make chisel BOARD=ulx3s PLLFREQ=15000000"; echo)
	$(MILL) $(project).run $(CHISEL_PARAMS) -td $@

check: test
.PHONY: test
test:## Run Chisel tests
	$(MILL) $(project).test
	@echo "If using WriteVcdAnnotation in your tests, the VCD files are generated in ./test_run_dir/testname directories."

.PHONY: lint
lint: ## Formats code using scalafmt and scalafix
	$(MILL) run lint

.PHONY: deps
deps: ## Check for library version updates
	$(MILL) run deps

MODULE ?= Toplevel
dot: $(generated_files) ## Generate dot files for Core
	@echo "Generating graphviz dot file for module \"$(MODULE)\". For a different module, pass the argument as \"make dot MODULE=mymod\"."
	@$(YOSYS) -p "read_verilog ./generated/*.v; proc; opt; show -colors 2 -width -format dot -prefix $(MODULE) -signed $(MODULE)"

.PHONY: clean
clean:   ## Clean all generated files
	$(MILL) clean
	@rm -rf obj_dir test_run_dir target
	@rm -rf $(generated_files)
	@rm -rf tmphex
	@rm -rf out
	@rm -f *.mem

.PHONY: clean-all
clean-all: clean  ## Clean all downloaded dependencies and cache
	@rm -rf project/.bloop
	@rm -rf project/project
	@rm -rf project/target
	@rm -rf .bloop .bsp .metals .vscode

.PHONY: help
help:
	@echo "Makefile targets:"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = "[:##]"}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$4}'
	@echo ""

.DEFAULT_GOAL := help