// RUN: scairrtl-opt --verify-diagnostics %s | filecheck %s

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %cond = "firrtl.constant"() <{value = 1 : i64}> : () -> !firrtl.uint<1>
      "firrtl.when"(%cond) : (!firrtl.uint<1>) -> ()
    }) : () -> ()
  }) : () -> ()
}

// CHECK: "builtin.module"() ({
// CHECK-NEXT:   "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:     "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:       %0 = "firrtl.constant"() <{value = 1 : i64}> : () -> !firrtl.uint<1>
// CHECK-NEXT:       "firrtl.when"(%0) : (!firrtl.uint<1>) -> ()
// CHECK-NEXT:       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// CHECK-NEXT:       > Operation 'firrtl.when' needs one or two body regions
// CHECK-NEXT:     }) : () -> ()
// CHECK-NEXT:   }) : () -> ()
// CHECK-NEXT: }) : () -> ()
