// RUN: scairrtl-opt %s | filecheck %s

builtin.module {
  "firrtl.circuit"() <{sym_name = "Top"}> ({
  ^bb0:
    "firrtl.module"() <{sym_name = "Child", port_directions = [], port_types = [], port_names = [], port_locations = [], port_annotations = [], port_symbols = [], convention = 0 : i32}> ({
    ^bb0:
      %a = "firrtl.wire"() <{sym_name = "a", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
      %b = "firrtl.wire"() <{sym_name = "b", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
      %s = "firrtl.wire"() <{sym_name = "s", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.sint<8>
      %sh = "firrtl.wire"() <{sym_name = "sh", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<3>
      %u = "firrtl.wire"() <{sym_name = "u", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint
      %sum = "firrtl.add"(%a, %b) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<9>
      %diff = "firrtl.sub"(%a, %b) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<9>
      %diff_signed = "firrtl.sub"(%a, %b) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.sint<9>
      %prod = "firrtl.mul"(%a, %b) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<16>
      %prod_signed = "firrtl.mul"(%s, %s) : (!firrtl.sint<8>, !firrtl.sint<8>) -> !firrtl.sint<16>
      %quot = "firrtl.div"(%a, %b) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<8>
      %quot_signed = "firrtl.div"(%s, %s) : (!firrtl.sint<8>, !firrtl.sint<8>) -> !firrtl.sint<9>
      %rem = "firrtl.rem"(%a, %b) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<8>
      %rem_signed = "firrtl.rem"(%s, %s) : (!firrtl.sint<8>, !firrtl.sint<8>) -> !firrtl.sint<8>
      %mul_unsized = "firrtl.mul"(%u, %a) : (!firrtl.uint, !firrtl.uint<8>) -> !firrtl.uint
      %and = "firrtl.and"(%a, %b) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<8>
      %and_signed = "firrtl.and"(%s, %s) : (!firrtl.sint<8>, !firrtl.sint<8>) -> !firrtl.uint<8>
      %or = "firrtl.or"(%a, %b) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<8>
      %xor = "firrtl.xor"(%a, %b) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<8>
      %not = "firrtl.not"(%s) : (!firrtl.sint<8>) -> !firrtl.uint<8>
      %andr = "firrtl.andr"(%a) : (!firrtl.uint<8>) -> !firrtl.uint<1>
      %orr = "firrtl.orr"(%a) : (!firrtl.uint<8>) -> !firrtl.uint<1>
      %xorr = "firrtl.xorr"(%a) : (!firrtl.uint<8>) -> !firrtl.uint<1>
      %cat = "firrtl.cat"(%a, %b) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<16>
      %dshl = "firrtl.dshl"(%a, %sh) : (!firrtl.uint<8>, !firrtl.uint<3>) -> !firrtl.uint<15>
      %dshr = "firrtl.dshr"(%a, %sh) : (!firrtl.uint<8>, !firrtl.uint<3>) -> !firrtl.uint<8>
      %pad = "firrtl.pad"(%a) : (!firrtl.uint<8>) -> !firrtl.uint<16>
      %pad_signed = "firrtl.pad"(%s) : (!firrtl.sint<8>) -> !firrtl.sint<16>
      %tail = "firrtl.tail"(%a) <{n = 2 : i32}> : (!firrtl.uint<8>) -> !firrtl.uint<6>
    }) : () -> ()
  }) : () -> ()
}
// CHECK: builtin.module {
// CHECK-NEXT:   "firrtl.circuit"() <{sym_name = "Top", annotations = []}> ({
// CHECK-NEXT:     "firrtl.module"() <{layers = [], port_locations = [], domain_info = [], port_types = [], sym_name = "Child", annotations = [], port_names = [], port_directions = [], port_annotations = [], convention = 0 : i32, port_symbols = []}> ({
// CHECK-NEXT:       %0 = "firrtl.wire"() <{sym_name = "a", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
// CHECK-NEXT:       %1 = "firrtl.wire"() <{sym_name = "b", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<8>
// CHECK-NEXT:       %2 = "firrtl.wire"() <{sym_name = "s", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.sint<8>
// CHECK-NEXT:       %3 = "firrtl.wire"() <{sym_name = "sh", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint<3>
// CHECK-NEXT:       %4 = "firrtl.wire"() <{sym_name = "u", nameKind = 0 : i32, annotations = []}> : () -> !firrtl.uint
// CHECK-NEXT:       %5 = "firrtl.add"(%0, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<9>
// CHECK-NEXT:       %6 = "firrtl.sub"(%0, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<9>
// CHECK-NEXT:       %7 = "firrtl.sub"(%0, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.sint<9>
// CHECK-NEXT:       %8 = "firrtl.mul"(%0, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<16>
// CHECK-NEXT:       %9 = "firrtl.mul"(%2, %2) : (!firrtl.sint<8>, !firrtl.sint<8>) -> !firrtl.sint<16>
// CHECK-NEXT:       %10 = "firrtl.div"(%0, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<8>
// CHECK-NEXT:       %11 = "firrtl.div"(%2, %2) : (!firrtl.sint<8>, !firrtl.sint<8>) -> !firrtl.sint<9>
// CHECK-NEXT:       %12 = "firrtl.rem"(%0, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<8>
// CHECK-NEXT:       %13 = "firrtl.rem"(%2, %2) : (!firrtl.sint<8>, !firrtl.sint<8>) -> !firrtl.sint<8>
// CHECK-NEXT:       %14 = "firrtl.mul"(%4, %0) : (!firrtl.uint, !firrtl.uint<8>) -> !firrtl.uint
// CHECK-NEXT:       %15 = "firrtl.and"(%0, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<8>
// CHECK-NEXT:       %16 = "firrtl.and"(%2, %2) : (!firrtl.sint<8>, !firrtl.sint<8>) -> !firrtl.uint<8>
// CHECK-NEXT:       %17 = "firrtl.or"(%0, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<8>
// CHECK-NEXT:       %18 = "firrtl.xor"(%0, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<8>
// CHECK-NEXT:       %19 = "firrtl.not"(%2) : (!firrtl.sint<8>) -> !firrtl.uint<8>
// CHECK-NEXT:       %20 = "firrtl.andr"(%0) : (!firrtl.uint<8>) -> !firrtl.uint<1>
// CHECK-NEXT:       %21 = "firrtl.orr"(%0) : (!firrtl.uint<8>) -> !firrtl.uint<1>
// CHECK-NEXT:       %22 = "firrtl.xorr"(%0) : (!firrtl.uint<8>) -> !firrtl.uint<1>
// CHECK-NEXT:       %23 = "firrtl.cat"(%0, %1) : (!firrtl.uint<8>, !firrtl.uint<8>) -> !firrtl.uint<16>
// CHECK-NEXT:       %24 = "firrtl.dshl"(%0, %3) : (!firrtl.uint<8>, !firrtl.uint<3>) -> !firrtl.uint<15>
// CHECK-NEXT:       %25 = "firrtl.dshr"(%0, %3) : (!firrtl.uint<8>, !firrtl.uint<3>) -> !firrtl.uint<8>
// CHECK-NEXT:       %26 = "firrtl.pad"(%0) : (!firrtl.uint<8>) -> !firrtl.uint<16>
// CHECK-NEXT:       %27 = "firrtl.pad"(%2) : (!firrtl.sint<8>) -> !firrtl.sint<16>
// CHECK-NEXT:       %28 = "firrtl.tail"(%0) <{n = 2 : i32}> : (!firrtl.uint<8>) -> !firrtl.uint<6>
// CHECK-NEXT:     }) : () -> ()
// CHECK-NEXT:   }) : () -> ()
// CHECK-NEXT: }
