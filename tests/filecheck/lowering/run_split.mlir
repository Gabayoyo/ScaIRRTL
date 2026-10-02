// RUN: choco-opt --run --split-input-file %s | filecheck %s
// REQUIRES: mlir-opt, mlir-runner

builtin.module {
  func.func @main() -> i32 {
    %0 = "arith.constant"() <{value = 3 : i32}> : () -> i32
    func.return %0 : i32
  }
}
// -----
builtin.module {
  func.func @main() -> i32 {
    %0 = "arith.constant"() <{value = 4 : i32}> : () -> i32
    func.return %0 : i32
  }
}

// CHECK: 3
// CHECK-NEXT: 4
