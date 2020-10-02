use crate::core::{Flag, CPU, Bus};
use crate::core::utils::is_bit_set;

impl CPU {
    // h1 destination
    // h2 source

    pub fn THUMB_HILO_ADD(&mut self, bus: &mut Bus, instr: u16) {
        let h1 = (is_bit_set(instr as u32, 7) as usize)*8;
        let h2 = (is_bit_set(instr as u32, 6) as usize)*8;

        let rs = self.register[((instr >> 3)&0x7) as usize + h2];
        let rd = (instr&0x7) as usize + h1;
        self.register_write(rd, self.register[rd] + rs, bus);
    }

    pub fn THUMB_HILO_MOV(&mut self, bus: &mut Bus, instr: u16) {
        let h1 = (is_bit_set(instr as u32, 7) as usize)*8;
        let h2 = (is_bit_set(instr as u32, 6) as usize)*8;

        let rs = self.register[((instr >> 3)&0x7) as usize + h2];
        self.register_write((instr&0x7) as usize + h1, rs, bus);
    }

    pub fn THUMB_HILO_CMP(&mut self, bus: &mut Bus, instr: u16) {
        let h1 = (is_bit_set(instr as u32, 7) as usize)*8;
        let h2 = (is_bit_set(instr as u32, 6) as usize)*8;

        let rs = self.register[((instr >> 3)&0x7) as usize + h2];
        let tmp = self.register[(instr&0x7) as usize + h1] - rs;

        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
    }

    pub fn THUMB_HILO_BX(&mut self, bus: &mut Bus, instr: u16) {
        let h2 = (is_bit_set(instr as u32, 6) as usize)*8;

        let rs = self.register[((instr >> 3)&0x7) as usize + h2];

        self.register[15] = rs;
        self.switch_state(bus, rs);
    }
}