// RUN: scairrtl-opt %s | filecheck %s
// RUN: scairrtl-opt -g %s | scairrtl-opt | filecheck %s --check-prefix=ROUNDTRIP

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %cond = "firrtl.constant"() <{value = 1 : i64}> : () -> !firrtl.uint<1>
      %zero = "firrtl.constant"() <{value = 0 : i64}> : () -> !firrtl.uint<8>
      %w = "firrtl.wire"() <{sym_name = "w", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
      "firrtl.when"(%cond) ({
      ^bb0:
        "firrtl.matchingconnect"(%w, %zero) : (!firrtl.uint<8>, !firrtl.uint<8>) -> ()
      }) : (!firrtl.uint<1>) -> ()
      "firrtl.when"(%cond) ({
      ^bb0:
        "firrtl.matchingconnect"(%w, %zero) : (!firrtl.uint<8>, !firrtl.uint<8>) -> ()
      }, {
      ^bb0:
        %inner = "firrtl.wire"() <{sym_name = "inner", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
        "firrtl.when"(%cond) ({
        ^bb0:
          "firrtl.matchingconnect"(%inner, %zero) : (!firrtl.uint<8>, !firrtl.uint<8>) -> ()
        }) : (!firrtl.uint<1>) -> ()
      }) : (!firrtl.uint<1>) -> ()
    }) : () -> ()
  }) : () -> ()
}

// CHECK: builtin.module {
// CHECK-NEXT:   "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:     "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:       %0 = "firrtl.constant"() <{value = 1 : i64}> : () -> !firrtl.uint<1>
// CHECK-NEXT:       %1 = "firrtl.constant"() <{value = 0 : i64}> : () -> !firrtl.uint<8>
// CHECK-NEXT:       %2 = "firrtl.wire"() <{sym_name = "w", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
// CHECK-NEXT:       "firrtl.when"(%0) ({
// CHECK-NEXT:         "firrtl.matchingconnect"(%2, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> ()
// CHECK-NEXT:       }) : (!firrtl.uint<1>) -> ()
// CHECK-NEXT:       "firrtl.when"(%0) ({
// CHECK-NEXT:         "firrtl.matchingconnect"(%2, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> ()
// CHECK-NEXT:       }, {
// CHECK-NEXT:         %3 = "firrtl.wire"() <{sym_name = "inner", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
// CHECK-NEXT:         "firrtl.when"(%0) ({
// CHECK-NEXT:           "firrtl.matchingconnect"(%3, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> ()
// CHECK-NEXT:         }) : (!firrtl.uint<1>) -> ()
// CHECK-NEXT:       }) : (!firrtl.uint<1>) -> ()
// CHECK-NEXT:     }) : () -> ()
// CHECK-NEXT:   }) : () -> ()
// CHECK-NEXT: }

// ROUNDTRIP: builtin.module {
// ROUNDTRIP-NEXT:   "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// ROUNDTRIP-NEXT:     "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// ROUNDTRIP-NEXT:       %0 = "firrtl.constant"() <{value = 1 : i64}> : () -> !firrtl.uint<1>
// ROUNDTRIP-NEXT:       %1 = "firrtl.constant"() <{value = 0 : i64}> : () -> !firrtl.uint<8>
// ROUNDTRIP-NEXT:       %2 = "firrtl.wire"() <{sym_name = "w", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
// ROUNDTRIP-NEXT:       "firrtl.when"(%0) ({
// ROUNDTRIP-NEXT:         "firrtl.matchingconnect"(%2, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> ()
// ROUNDTRIP-NEXT:       }) : (!firrtl.uint<1>) -> ()
// ROUNDTRIP-NEXT:       "firrtl.when"(%0) ({
// ROUNDTRIP-NEXT:         "firrtl.matchingconnect"(%2, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> ()
// ROUNDTRIP-NEXT:       }, {
// ROUNDTRIP-NEXT:         %3 = "firrtl.wire"() <{sym_name = "inner", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
// ROUNDTRIP-NEXT:         "firrtl.when"(%0) ({
// ROUNDTRIP-NEXT:           "firrtl.matchingconnect"(%3, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> ()
// ROUNDTRIP-NEXT:         }) : (!firrtl.uint<1>) -> ()
// ROUNDTRIP-NEXT:       }) : (!firrtl.uint<1>) -> ()
// ROUNDTRIP-NEXT:     }) : () -> ()
// ROUNDTRIP-NEXT:   }) : () -> ()
// ROUNDTRIP-NEXT: }
