use crate::core::{CPU, Bus, Flag};

impl CPU {
    pub fn THUMB_AND(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[((instr >> 3)&0x7) as usize];
        let d = (instr&0x7) as usize;
        let tmp = self.register[d]&rs;
        self.register[d] = tmp;

        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
    }

    pub fn THUMB_EOR(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[((instr >> 3)&0x7) as usize];
        let rd = &mut self.register[(instr&0x7) as usize];
        *rd = *rd^rs;
        let rd = *rd;

        self.set_flag(Flag::Z, rd == 0);
        self.set_flag(Flag::N, rd&0x80000000 != 0);
    }

    pub fn THUMB_ORR(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[((instr >> 3)&0x7) as usize];
        let d = (instr&0x7) as usize;
        let tmp = self.register[d] | rs;
        self.register[d] = tmp;

        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
    }

    pub fn THUMB_MUL(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[((instr >> 3)&0x7) as usize];
        let rd = &mut self.register[(instr&0x7) as usize];
        *rd = *rd*rs;
        let rd = *rd;

        self.set_flag(Flag::Z, rd == 0);
        self.set_flag(Flag::N, rd&0x80000000 != 0);
    }

    pub fn THUMB_BIC(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[((instr >> 3)&0x7) as usize];
        let d = (instr&0x7) as usize;
        let tmp = self.register[d] & !rs;
        self.register[d] = tmp;

        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
    }

    pub fn THUMB_LSL(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[((instr >> 3)&0x7) as usize];
        let d = (instr&0x7) as usize;

        let (tmp, carry) = self.barrel_shifter_logical_left(self.register[d], rs);

        self.register[d] = tmp;

        self.set_flag(Flag::C, carry);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
    }

    pub fn THUMB_LSR(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[((instr >> 3)&0x7) as usize];
        let d = (instr&0x7) as usize;

        let (tmp, carry) = self.barrel_shifter_logical_right(self.register[d], rs);

        self.register[d] = tmp;

        self.set_flag(Flag::C, carry);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
    }

    pub fn THUMB_ASR(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[((instr >> 3)&0x7) as usize];
        let d = (instr&0x7) as usize;

        let (tmp, carry) = self.barrel_shifter_arithmethic_right(self.register[d], rs);

        self.register[d] = tmp;

        self.set_flag(Flag::C, carry);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
    }

    pub fn THUMB_ROR(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[((instr >> 3)&0x7) as usize];
        let d = (instr&0x7) as usize;

        let (tmp, carry) = self.barrel_shifter_rotate_right(self.register[d], rs);

        self.register[d] = tmp;

        self.set_flag(Flag::C, carry);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
    }

    pub fn THUMB_ADC(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[((instr >> 3)&0x7) as usize];
        let d = (instr&0x7) as usize;
        let c = (self.cpsr&Flag::C != 0) as u64;

        self.set_flag(Flag::V, (self.register[d] as i32).overflowing_add((rs+c as u32) as i32).1);
        let tmp = self.register[d] as u64 + rs as u64 + c;
        self.register[d] = tmp as u32;

        self.set_flag(Flag::N, tmp&0x80000000 != 0);
        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::C, tmp > u32::MAX as u64);
    }

    pub fn THUMB_SBC(&mut self, bus: &mut Bus, instr: u16) {
        let rs = self.register[((instr >> 3)&0x7) as usize];
        let d = (instr&0x7) as usize;
        let c = (self.cpsr&Flag::C == 0) as u32;
        
        let (tmp, overflow) = (self.register[d] as i32).overflowing_sub((rs + c) as i32);


        let v1 = self.register[d].overflowing_sub(rs);
        let v2 = v1.0.overflowing_sub(c);

        self.set_flag(Flag::C, !(v1.1||v2.1));
        self.set_flag(Flag::V, overflow);
        self.set_flag(Flag::N, tmp < 0);
        self.set_flag(Flag::Z, tmp == 0);

        self.register[d] = tmp as u32;
    }

    pub fn THUMB_TST(&mut self, bus: &mut Bus, instr: u16) {
        let rd = self.register[(instr&0x7) as usize];
        let rs = self.register[((instr >> 3)&0x7) as usize];

        let tmp = rs&rd;

        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::N, tmp&0x80000000 != 0);
    }

    pub fn THUMB_CMP(&mut self, bus: &mut Bus, instr: u16) {
        let rd = self.register[(instr&0x7) as usize];
        let rs = self.register[((instr >> 3)&0x7) as usize];

        let (tmp, overflow) = (rd as i32).overflowing_sub(rs as i32);

        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::N, tmp < 0);
        self.set_flag(Flag::C, rd >= rs);
        self.set_flag(Flag::V, overflow);
    }

    pub fn THUMB_CMN(&mut self, bus: &mut Bus, instr: u16) {
        let rd = self.register[(instr&0x7) as usize];
        let rs = self.register[((instr >> 3)&0x7) as usize];

        let (tmp, overflow) = (rs as i32).overflowing_add(rd as i32);

        self.set_flag(Flag::Z, tmp == 0);
        self.set_flag(Flag::N, tmp < 0);
        self.set_flag(Flag::V, overflow);
        self.set_flag(Flag::C, rs.overflowing_add(rd).1);
    }

    pub fn THUMB_NEG(&mut self, bus: &mut Bus, instr: u16) {
        let rs = -(self.register[((instr >> 3)&0x7) as usize] as i32);
        self.register[(instr&0x7) as usize] = rs as u32;

        self.set_flag(Flag::Z, rs == 0);
        self.set_flag(Flag::N, rs as u32&0x80000000 != 0);
    }

    pub fn THUMB_MVN(&mut self, bus: &mut Bus, instr: u16) {
        let rs = !self.register[((instr >> 3)&0x7) as usize];
        self.register[(instr&0x7) as usize] = rs;

        self.set_flag(Flag::Z, rs == 0);
        self.set_flag(Flag::N, rs&0x80000000 != 0);
    }
}