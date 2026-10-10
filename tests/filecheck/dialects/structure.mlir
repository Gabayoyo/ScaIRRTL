// RUN: scairrtl-opt %s | filecheck %s

builtin.module {
  "scairrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "scairrtl.layer"() <{sym_name = "Verif", convention = 0 : i32}> ({
    ^bb0:
    }) : () -> ()
    "scairrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [!scairrtl.clock], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
    }) : () -> ()
  }) : () -> ()
}

// CHECK:       builtin.module {
// CHECK-NEXT:    "scairrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "scairrtl.layer"() <{sym_name = "Verif", convention = 0 : i32}> ({
// CHECK-NEXT:      ^bb0:
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:      "scairrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [!scairrtl.clock], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:      ^bb0:
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }
