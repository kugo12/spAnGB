#[allow(non_camel_case_types)]
use crate::core::{Bus, CPU};

pub struct spAnGB {
    pub bus: Bus,
    cpu: CPU
}

impl spAnGB {
    pub fn new() -> Self {
        Self {
            bus: Bus::new(),
            cpu: CPU::new()
        }
    }

    pub fn run(&mut self) {
        loop {
            self.cpu.tick(&mut self.bus);
            self.bus.tick();
        }
    }
}