// RUN: scairrtl-opt -a -s %s | filecheck %s

builtin.module {
  "example.unknown"() : () -> ()
}

// CHECK: builtin.module {
// CHECK: "example.unknown"() : () -> ()
