// RUN: scairrtl-opt %s | filecheck %s

builtin.module {
  "scairrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "scairrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %a = "scairrtl.wire"() <{sym_name = "a", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
      %b = "scairrtl.wire"() <{sym_name = "b", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
      %e = "scairrtl.eq"(%a, %b) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> !scairrtl.uint<1>
      %ne = "scairrtl.neq"(%a, %b) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> !scairrtl.uint<1>
      %lt = "scairrtl.lt"(%a, %b) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> !scairrtl.uint<1>
      %le = "scairrtl.leq"(%a, %b) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> !scairrtl.uint<1>
      %gt = "scairrtl.gt"(%a, %b) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> !scairrtl.uint<1>
      %ge = "scairrtl.geq"(%a, %b) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> !scairrtl.uint<1>
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       builtin.module {
// CHECK-NEXT:    "scairrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "scairrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "scairrtl.wire"() <{sym_name = "a", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
// CHECK-NEXT:        %1 = "scairrtl.wire"() <{sym_name = "b", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.uint<8>
// CHECK-NEXT:        %2 = "scairrtl.eq"(%0, %1) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> !scairrtl.uint<1>
// CHECK-NEXT:        %3 = "scairrtl.neq"(%0, %1) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> !scairrtl.uint<1>
// CHECK-NEXT:        %4 = "scairrtl.lt"(%0, %1) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> !scairrtl.uint<1>
// CHECK-NEXT:        %5 = "scairrtl.leq"(%0, %1) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> !scairrtl.uint<1>
// CHECK-NEXT:        %6 = "scairrtl.gt"(%0, %1) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> !scairrtl.uint<1>
// CHECK-NEXT:        %7 = "scairrtl.geq"(%0, %1) : (!scairrtl.uint<8>, !scairrtl.uint<8>) -> !scairrtl.uint<1>
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }
