use crate::core::{CPU, Bus};

impl CPU {
    #[inline]
    pub fn tick_ARM(&mut self, bus: &mut Bus) {
        let instr = self.pipeline[2];

        if self.is_condition((instr >> 28) as u8) {
            self.lut_arm[(((instr&0xF0) >> 4) | ((instr&0xFF00000) >> 16)) as usize](self, bus, instr);
        }
    
        self.arm_step_pipeline(bus);
    }

    #[inline]
    pub fn arm_step_pipeline(&mut self, bus: &mut Bus) {
        self.pipeline[2] = self.pipeline[1];
        self.pipeline[1] = self.pipeline[0];
        self.register[15] += 4;
        self.pipeline[0] = bus.read32(self.register[15]);
    }

    pub fn arm_fill_pipeline(&mut self, bus: &mut Bus) {
        self.pipeline[2] = bus.read32(self.register[15]);
        self.register[15] += 4;
        self.pipeline[1] = bus.read32(self.register[15]);
        self.register[15] += 4;
        self.pipeline[0] = bus.read32(self.register[15]);
    }
}