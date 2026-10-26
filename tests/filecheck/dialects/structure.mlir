// RUN: scairrtl-opt %s | filecheck %s

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.layer"() <{sym_name = "Verif", convention = 0 : i32}> ({
    ^bb0:
    }) : () -> ()
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [!firrtl.clock], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32, layers = [@Verif]}> ({
    ^bb0:
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       builtin.module {
// CHECK-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "firrtl.layer"() <{sym_name = "Verif", convention = 0 : i32}> ({
// CHECK-NEXT:      ^bb0:
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:      "firrtl.module"() <{layers = [@Verif], port_locations = [], domain_info = [], port_types = [!firrtl.clock], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:      ^bb0:
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }
