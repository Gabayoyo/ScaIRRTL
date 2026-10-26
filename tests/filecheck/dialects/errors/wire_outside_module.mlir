// RUN: scairrtl-opt --verify-diagnostics %s | filecheck %s

builtin.module {
  func.func @f() {
    %w = "firrtl.wire"() <{sym_name = "w", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
    func.return
  }
}

// CHECK:       "builtin.module"() ({
// CHECK-NEXT:    "func.func"() <{sym_name = "f", function_type = () -> ()}> ({
// CHECK-NEXT:      %0 = "firrtl.wire"() <{sym_name = "w", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
// CHECK-NEXT:      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// CHECK-NEXT:      > Operation 'firrtl.wire' must be nested in ModuleOp
// CHECK-NEXT:      "func.return"() : () -> ()
// CHECK-NEXT:    }) : () -> ()
// CHECK-NEXT:  }) : () -> ()
