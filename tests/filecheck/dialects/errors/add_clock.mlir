// RUN: scairrtl-opt --verify-diagnostics %s | filecheck %s

builtin.module {
  "scairrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "scairrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %c = "scairrtl.wire"() <{sym_name = "c", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.clock
      %d = "scairrtl.wire"() <{sym_name = "d", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.clock
      %s = "scairrtl.add"(%c, %d) : (!scairrtl.clock, !scairrtl.clock) -> !scairrtl.clock
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       "builtin.module"() ({
// CHECK-NEXT:    "scairrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "scairrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "scairrtl.wire"() <{sym_name = "c", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.clock
// CHECK-NEXT:        %1 = "scairrtl.wire"() <{sym_name = "d", nameKind = 0 : i32, annotations = []}> : () -> !scairrtl.clock
// CHECK-NEXT:        %2 = "scairrtl.add"(%0, %1) : (!scairrtl.clock, !scairrtl.clock) -> !scairrtl.clock
// CHECK-NEXT:        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// CHECK-NEXT:        > Operation 'scairrtl.add' has ill-typed operands or results: scairrtl.clock, expected IntType, scairrtl.clock, expected IntType, scairrtl.clock, expected IntType
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }) : () -> ()
