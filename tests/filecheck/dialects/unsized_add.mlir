// RUN: scairrtl-opt %s | filecheck %s

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %a = "firrtl.wire"() <{sym_name = "a", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint
      %b = "firrtl.wire"() <{sym_name = "b", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint
      %s = "firrtl.add"(%a, %b) : (!firrtl.uint, !firrtl.uint) -> !firrtl.uint
    }) : () -> ()
  }) : () -> ()
}
// CHECK:       builtin.module {
// CHECK-NEXT:    "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:      "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:        %0 = "firrtl.wire"() <{sym_name = "a", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint
// CHECK-NEXT:        %1 = "firrtl.wire"() <{sym_name = "b", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint
// CHECK-NEXT:        %2 = "firrtl.add"(%0, %1) : (!firrtl.uint, !firrtl.uint) -> !firrtl.uint
// CHECK-NEXT:      }) : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }
