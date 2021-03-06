use crate::core::{CPU, Bus, cpu::CPU_state};

impl CPU {
    #[inline]
    pub fn tick_THUMB(&mut self, bus: &mut Bus) {
        let instr = self.pipeline[2];

        self.lut_thumb[((instr >> 6)&0x3FF) as usize](self, bus, instr as u16);
        if self.state == CPU_state::ARM {
            self.arm_step_pipeline(bus);
            return
        }

        self.thumb_step_pipeline(bus);
    }

    #[inline]
    pub fn thumb_step_pipeline(&mut self, bus: &mut Bus) {
        self.pipeline[2] = self.pipeline[1];
        self.pipeline[1] = self.pipeline[0];
        self.register[15] += 2;
        self.pipeline[0] = bus.read16(self.register[15]) as u32;
    }

    #[inline]
    pub fn thumb_refill_pipeline(&mut self, bus: &mut Bus) {
        self.register[15] &= !1;
        self.pipeline[1] = bus.read16(self.register[15]) as u32;
        self.register[15] += 2;
        self.pipeline[0] = bus.read16(self.register[15]) as u32;
    }

    pub fn thumb_fill_pipeline(&mut self, bus: &mut Bus) {
        self.pipeline[2] = bus.read16(self.register[15]) as u32;
        self.register[15] += 2;
        self.pipeline[1] = bus.read16(self.register[15]) as u32;
        self.register[15] += 2;
        self.pipeline[0] = bus.read16(self.register[15]) as u32;
    }
}