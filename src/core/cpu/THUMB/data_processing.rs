use crate::core::{CPU, Bus, Flag};

impl CPU {
    pub fn THUMB_AND(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];
        let rd = &mut self.register[((instr >> 3)&0x7) as usize];
        *rd = *rd&rs;
        let rd = *rd;

        self.set_flag(Flag::Z, rd == 0);
        self.set_flag(Flag::N, rd&0x80000000 != 0);
    }

    pub fn THUMB_EOR(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];
        let rd = &mut self.register[((instr >> 3)&0x7) as usize];
        *rd = *rd^rs;
        let rd = *rd;

        self.set_flag(Flag::Z, rd == 0);
        self.set_flag(Flag::N, rd&0x80000000 != 0);
    }

    pub fn THUMB_ORR(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];
        let rd = &mut self.register[((instr >> 3)&0x7) as usize];
        *rd = *rd|rs;
        let rd = *rd;

        self.set_flag(Flag::Z, rd == 0);
        self.set_flag(Flag::N, rd&0x80000000 != 0);
    }

    pub fn THUMB_MUL(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];
        let rd = &mut self.register[((instr >> 3)&0x7) as usize];
        *rd = *rd*rs;
        let rd = *rd;

        self.set_flag(Flag::Z, rd == 0);
        self.set_flag(Flag::N, rd&0x80000000 != 0);
    }

    pub fn THUMB_BIC(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];
        let rd = &mut self.register[((instr >> 3)&0x7) as usize];
        *rd = *rd& !rs;
        let rd = *rd;

        self.set_flag(Flag::Z, rd == 0);
        self.set_flag(Flag::N, rd&0x80000000 != 0);
    }

    pub fn THUMB_LSL(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];

        let d = ((instr >> 3)&0x7) as usize;
        let (tmp, carry) = self.barrel_shifter_logical_left(self.register[d], rs);

        self.register[d] = tmp;

        self.set_flag(Flag::C, carry);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
    }

    pub fn THUMB_LSR(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];

        let d = ((instr >> 3)&0x7) as usize;
        let (tmp, carry) = self.barrel_shifter_logical_right(self.register[d], rs);

        self.register[d] = tmp;

        self.set_flag(Flag::C, carry);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
    }

    pub fn THUMB_ASR(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];

        let d = ((instr >> 3)&0x7) as usize;
        let (tmp, carry) = self.barrel_shifter_arithmethic_right(self.register[d], rs);

        self.register[d] = tmp;

        self.set_flag(Flag::C, carry);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
    }

    pub fn THUMB_ROR(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];

        let d = ((instr >> 3)&0x7) as usize;
        let (tmp, carry) = self.barrel_shifter_rotate_right(self.register[d], rs);

        self.register[d] = tmp;

        self.set_flag(Flag::C, carry);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
    }

    pub fn THUMB_ADC(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];
        let d = ((instr >> 3)&0x7) as usize;
        let c = (self.cpsr&Flag::C != 0) as u32;

        self.register[d] += rs + c;
        let tmp = self.register[d];

        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
    }

    pub fn THUMB_SBC(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];
        let d = ((instr >> 3)&0x7) as usize;
        let c = (self.cpsr&Flag::C == 0) as u32;

        self.register[d] -= rs - c;
        let tmp = self.register[d];

        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
    }

    pub fn THUMB_TST(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];
        let rd = self.register[((instr >> 3)&0x7) as usize];

        let tmp = rs&rd;

        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
    }

    pub fn THUMB_CMP(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];
        let rd = self.register[((instr >> 3)&0x7) as usize];

        let tmp = rs - rd;

        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
    }

    pub fn THUMB_CMN(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[(instr&0x7) as usize];
        let rd = self.register[((instr >> 3)&0x7) as usize];

        let tmp = rs + rd;

        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
    }

    pub fn THUMB_NEG(&mut self, bus: &mut Bus, instr: u16) {
        let rs = -(self.register[(instr&0x7) as usize] as i32);
        self.register[((instr >> 3)&0x7) as usize] = rs as u32;

        self.set_flag(Flag::Z, rs == 0);
        self.set_flag(Flag::N, rs as u32&0x80000000 != 0);
    }

    pub fn THUMB_MVN(&mut self, bus: &mut Bus, instr: u16) {
        let rs = !self.register[(instr&0x7) as usize];
        self.register[((instr >> 3)&0x7) as usize] = rs;

        self.set_flag(Flag::Z, rs == 0);
        self.set_flag(Flag::N, rs&0x80000000 != 0);
    }
}