// RUN: choco-opt %s | filecheck %s --check-prefix=FILE
// RUN: choco-opt < %s | filecheck %s --check-prefix=STDIN
// RUN: choco-opt -g %s | filecheck %s --check-prefix=GENERIC

builtin.module {
  %0 = "arith.constant"() <{value = 7 : i32}> : () -> i32
}

// FILE: builtin.module {
// FILE: "arith.constant"

// STDIN: builtin.module {
// STDIN: "arith.constant"

// GENERIC: "builtin.module"
// GENERIC: "arith.constant"
