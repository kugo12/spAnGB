use crate::core::{Flag, CPU, Bus};
use crate::core::utils::is_bit_set;

impl CPU {
    // h1 destination
    // h2 source

    pub fn THUMB_HILO_OPERANDS(&mut self, instr: u16) -> (u32, u32, usize) {
        let h1 = (is_bit_set(instr as u32, 7) as usize)*8;
        let h2 = (is_bit_set(instr as u32, 6) as usize)*8;

        // self.register[15] += 2;
        let tmp = self.register[15];
        self.register[15] = self.register[15]& !2;
        let out = (
            self.register[(instr&0x7) as usize + h1],
            self.register[((instr >> 3)&0x7) as usize + h2],
            (instr&0x7) as usize + h1
        );
        // self.register[15] -= 2;
        self.register[15] = tmp;
        out
    }

    pub fn THUMB_HILO_ADD(&mut self, bus: &mut Bus, instr: u16) {
        let h1 = (is_bit_set(instr as u32, 7) as usize)*8;
        let h2 = (is_bit_set(instr as u32, 6) as usize)*8;
        let (rd, rs, d) = (
            self.register[(instr&0x7) as usize + h1],
            self.register[((instr >> 3)&0x7) as usize + h2],
            (instr&0x7) as usize + h1
        );
        // let (rd, rs, d) = self.THUMB_HILO_OPERANDS(instr);

        let (tmp, carry) = rd.overflowing_add(rs);

        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::C, carry);
        self.set_flag(Flag::V, (rd as i32).overflowing_add(rs as i32).1);

        self.register_write(d, tmp, bus);
    }

    pub fn THUMB_HILO_MOV(&mut self, bus: &mut Bus, instr: u16) {
        let (rd, rs, d) = self.THUMB_HILO_OPERANDS(instr);

        self.register_write(d, rs, bus);
    }

    pub fn THUMB_HILO_CMP(&mut self, bus: &mut Bus, instr: u16) {
        let (rd, rs, _) = self.THUMB_HILO_OPERANDS(instr);

        let (tmp, carry) = rd.overflowing_sub(rs);

        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::C, carry);
        self.set_flag(Flag::V, (rd as i32).overflowing_sub(rs as i32).1)
    }

    pub fn THUMB_HILO_BX(&mut self, bus: &mut Bus, instr: u16) {
        let h2 = (is_bit_set(instr as u32, 6) as usize)*8;

        let rs = self.register[((instr >> 3)&0x7) as usize + h2];

        self.register[15] = rs;
        self.switch_state(bus, rs);
    }
}