// RUN: scairrtl-opt --verify-diagnostics %s | filecheck %s

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %c = "firrtl.wire"() <{sym_name = "c", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.clock
      %d = "firrtl.wire"() <{sym_name = "d", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.clock
      %s = "firrtl.add"(%c, %d) : (!firrtl.clock, !firrtl.clock) -> !firrtl.clock
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       "builtin.module"() ({
// CHECK-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "firrtl.wire"() <{sym_name = "c", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.clock
// CHECK-NEXT:        %1 = "firrtl.wire"() <{sym_name = "d", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.clock
// CHECK-NEXT:        %2 = "firrtl.add"(%0, %1) : (!firrtl.clock, !firrtl.clock) -> !firrtl.clock
// CHECK-NEXT:        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// CHECK-NEXT:        > Operation 'firrtl.add' has ill-typed operands or results: firrtl.clock, expected IntType, firrtl.clock, expected IntType, firrtl.clock, expected IntType
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }) : () -> ()
