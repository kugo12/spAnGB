const BIT_TABLE: [u32; 32] = [
    0x1, 0x2, 0x4, 0x8, 0x10, 0x20, 0x40, 0x80, 0x100, 0x200, 0x400, 0x800, 0x1000, 0x2000, 0x4000, 0x8000,
    0x10000, 0x20000, 0x40000, 0x80000, 0x100000, 0x200000, 0x400000, 0x800000, 0x1000000, 0x2000000, 0x4000000, 0x8000000,
    0x10000000, 0x20000000, 0x40000000, 0x80000000 
];


pub fn is_bit_set(val: u32, n: usize) -> bool {
     val & BIT_TABLE[n] != 0
}

#[macro_export]
macro_rules! bus_read_arr {
    (u16, $arr:expr, $addr:expr) => {
        u16::from_le_bytes([$arr[$addr], $arr[$addr+1]])
    };
    (u32, $arr:expr, $addr:expr) => {
        u32::from_le_bytes([$arr[$addr], $arr[$addr+1], $arr[$addr+2], $arr[$addr+3]])
    };
}

#[macro_export]
macro_rules! bus_write_arr {
    (u16, $arr:expr, $addr:expr, $val:expr) => {
        $arr[$addr] = $val as u8;
        $arr[$addr+1] = ($val >> 8) as u8;
    };
    (u32, $arr:expr, $addr:expr, $val:expr) => {
        $arr[$addr] = $val as u8;
        $arr[$addr+1] = ($val >> 8) as u8;
        $arr[$addr+2] = ($val >> 16) as u8;
        $arr[$addr+3] = ($val >> 24) as u8;
    };
}
