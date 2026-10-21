<p align="center"><img src="docs/logo.svg" width="96" alt="" /></p>

# recmill

recmill is an offline command-line tool for people who work with fixed-width records, including developers, analysts and operators handling legacy or mainframe data. It reads COBOL-style copybook layouts to inspect, decode, encode, validate, profile and compare records.

[![CI](https://github.com/Gulliby-development/recmill/actions/workflows/ci.yml/badge.svg)](https://github.com/Gulliby-development/recmill/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## Why

Mainframe and legacy systems export one record per line with fields at fixed columns, and the only description of them is a copybook. recmill reads that layout text directly, so you can look up columns, decode a file to readable values or CSV, build records back, and check or profile a file without any other tool.

## Install

Install Java 21 and Maven. From the project directory, run `mvn -B verify` to build and test the project. Start the program with `java -cp target/classes io.github.gulliby.recmill.Main COMMAND`, replacing `COMMAND` with one of the commands below and adding its arguments.

## Usage

The command-line forms are:

```text
recmill layout [--groups] LAYOUT
recmill find LAYOUT COLUMN
recmill decode LAYOUT [NAME...]
recmill encode LAYOUT [NAME...]
recmill csv [--no-header] LAYOUT [NAME...]
recmill validate [--max MAX] LAYOUT [NAME...]
recmill profile LAYOUT [NAME...]
recmill diff LAYOUT1 LAYOUT2
```

For example, run `java -cp target/classes io.github.gulliby.recmill.Main layout "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99."` for the layout listing:

```text
path  from  to  pic
ID    1     4   9(4)
NAME  5     10  X(6)
AMT   11    15  S9(3)V9(2)
length      15
```

Run `java -cp target/classes io.github.gulliby.recmill.Main decode "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99."` with standard input `0001ALICE 0015{` followed by LF and `0002BOB   0120}` followed by LF to decode two records:

```text
ID=1
NAME=ALICE
AMT=1.50

ID=2
NAME=BOB
AMT=-12.00
```

To compare layouts, run `java -cp target/classes io.github.gulliby.recmill.Main diff "01 ORDER. 05 ID PIC 9(4). 05 NAME PIC X(6). 05 AMT PIC S9(3)V99." "01 ORDER. 05 ID PIC 9(5). 05 NAME PIC X(6). 05 NOTE PIC X(4). 05 FILLER PIC X(2)."`:

```text
- AMT from 11 length 5 pic S9(3)V9(2)
~ ID length 4 -> 5, pic 9(4) -> 9(5)
~ NAME from 5 -> 6
+ NOTE from 12 length 4 pic X(4)
removed     1
changed     2
added       1
same        0
record      15 -> 17
```

## Layouts

A layout is copybook-like text with numbered levels, names and clauses such as `PIC`, `OCCURS` and `REDEFINES`. Pictures use `X` for alphanumeric fields and `9` with an optional `V` for numeric fields. Groups contain child entries, occurrences repeat a field or group, and redefining entries provide alternate views over the same columns.

Copybooks often carry two more clauses, and recmill reads them without acting on them: `USAGE IS DISPLAY` (also `USAGE DISPLAY` or just `DISPLAY`), which is the only usage that recmill supports, and `VALUE [IS] operand`, where the operand is a number such as `0` or `-12.50`, `SPACE`, `SPACES`, `ZERO`, `ZEROS`, `ZEROES` or a literal in single or double quotes (a literal may hold spaces and periods; a doubled quote stands for one quote). A value never changes how a record is decoded, encoded or checked.

## Commands

`layout` lists field paths and their one-based columns; use `--groups` to include groups.

`find` reports the fields covering a requested column.

`decode` reads fixed-width records and prints selected fields as path/value lines.

`encode` builds fixed-width records from path/value assignments on standard input.

`csv` decodes records as CSV, with an optional header.

`validate` reports record or field problems and summarizes the input.

`profile` summarizes blanks, invalid values, distinct values and value ranges.

`diff` compares fields and record lengths in two layouts.

## Exit codes

The program exits with code 0 when a command succeeds, 1 when validation finds invalid records or a layout comparison finds differences, and 2 when an error prevents the command from completing.

## Development

Run `mvn -B -q verify` to compile the project and run the full automated test suite.

## License

MIT, see [LICENSE](LICENSE).

---

Built with BAKED
