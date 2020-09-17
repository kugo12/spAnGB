use crate::core::{CPU, Bus};

impl CPU {
    #[inline]
    pub fn tick_THUMB(&mut self, bus: &mut Bus) {
        let instr = self.register[2];

        self.lut_thumb[((instr >> 6)&0x3FF) as usize](self, bus, instr as u16);

        self.thumb_step_pipeline(bus);
    }

    #[inline]
    pub fn thumb_step_pipeline(&mut self, bus: &mut Bus) {
        self.pipeline[2] = self.pipeline[1];
        self.pipeline[1] = self.pipeline[0];
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