// RUN: choco-opt --split-input-file %s | filecheck %s

builtin.module {
}
// -----
builtin.module {
}

// CHECK: builtin.module {
// CHECK: // -----
// CHECK: builtin.module {
