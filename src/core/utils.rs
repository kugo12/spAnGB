#[inline(always)]
pub fn is_bit_set(val: u32, n: usize) -> bool {
    val& (1 << n) != 0
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
