use raylib::prelude::*;

use crate::core::ppu::io::*;
use crate::core::io::MemoryMappedRegister;

const ScreenTextureSize: Rectangle = Rectangle {
    height: 160.,
    width: 240.,
    x: 0.,
    y: 0.
};

const WH_RATIO: f32 = 240./160.;
const REFRESH_RATE: usize = 16_000_000 / 60;

const HDRAW_CYCLES: u32 = 960;
const HBLANK_CYCLES: u32 = 272;
const SCANLINE_CYCLES: u32 = HDRAW_CYCLES + HBLANK_CYCLES;

const VBLANK_HEIGHT: u16 = 68;
const VDRAW_HEIGHT: u16 = 160;


fn u16_to_color(c: u16) -> [u8; 3] {
    [
        (c as u8&0x1F) << 3,
        ((c >> 5) as u8&0x1F) << 3,
        ((c >> 10) as u8&0x1F) << 3
    ]
}

enum PPU_state {
    HDRAW  { cycles_left: u32 },
    VBLANK { cycles_left: u32 },
    HBLANK { cycles_left: u32 }
}

impl PPU_state {
    pub fn new_hdraw() -> Self {
        Self::HDRAW {
            cycles_left: HDRAW_CYCLES
        }
    }
    pub fn new_hblank() -> Self {
        Self::HBLANK {
            cycles_left: HBLANK_CYCLES
        }
    }
    pub fn new_vblank() -> Self {
        Self::VBLANK {
            cycles_left: SCANLINE_CYCLES
        }
    }
}


struct Draw {
    handle: RaylibHandle,
    thread: RaylibThread,

    screen_texture: Texture2D,
    pub buffer: Box<[u8; 240*160*3]>,
    screen_position: Vector2,
    screen_size: Rectangle,
}

impl Draw {
    fn new() -> Self {
        let (mut handle, thread) = init()
            .size(240, 160)
            .resizable()
            .title(&"spAnGB")
            .build();

        let mut img = Image::gen_image_color(240, 160, Color::BLACK);
        img.set_format(PixelFormat::UNCOMPRESSED_R8G8B8);
        let txt = handle.load_texture_from_image(&thread, &img).unwrap();
        
        Self {
            handle: handle,
            thread: thread,

            screen_texture: txt,
            buffer: Box::new([100; 240*160*3]),
            screen_position: Vector2::new(0., 0.),
            screen_size: ScreenTextureSize
        }
    }

    fn new_frame(&mut self) {
        if self.handle.window_should_close() { panic!("Window exit") }
        if self.handle.is_window_resized() {
            let h = self.handle.get_screen_height() as f32;
            let w = WH_RATIO * h;
            let x = (w - self.handle.get_screen_width() as f32)/2.;

            self.screen_size = Rectangle::new(0., 0., w, h);
            self.screen_position = Vector2::new(x, 0.);
        }

        self.screen_texture.update_texture(self.buffer.as_mut());

        let mut draw = self.handle.begin_drawing(&self.thread);
        draw.clear_background(Color::BLACK);
        draw.draw_texture_pro(&self.screen_texture, ScreenTextureSize, self.screen_size, self.screen_position, 0., Color::WHITE);
        draw.draw_fps(0, 0);
    }
}


pub struct PPU {
    draw: Draw,
    state: PPU_state,

    palette_ram: [u8; 1024],
    vram: [u8; 96*1024],
    obj_attrib: [u8; 1024],

    pub dispstat: DisplayStat,
    pub dispcnt: DisplayControl,
    pub vcount: VCount
}

impl PPU {
    pub fn new() -> Self {
        Self {
            draw: Draw::new(),
            state: PPU_state::new_hdraw(),

            palette_ram: [0; 1024],
            vram: [0; 96*1024],
            obj_attrib: [0; 1024],

            dispstat: DisplayStat::new(),
            dispcnt: DisplayControl::new(),
            vcount: VCount::new()
        }
    }

    pub fn change_background(&mut self, background: u16) {
        println!("git, {:x}", background);
    }

    pub fn read8(&self, addr: u32) -> u8 {
        match (addr >> 24)&0xF {
            5 => {
                self.palette_ram[(addr&0x3FF) as usize]
            },
            6 => {
                self.vram[(addr&0x17FFF) as usize]
            },
            7 => {
                self.obj_attrib[(addr&0x3FF) as usize]
            },
            _ => unreachable!()
        }
    }

    pub fn read16(&self, mut addr: u32) -> u16 {
        match (addr >> 24)&0xF {
            5 => {
                addr &= 0x3FF;
                bus_read_arr!(u16, self.palette_ram, addr as usize)
            },
            6 => {
                addr &= 0x17FFF;
                bus_read_arr!(u16, self.vram, addr as usize)
            },
            7 => {
                addr &= 0x3FF;
                bus_read_arr!(u16, self.obj_attrib, addr as usize)
            },
            _ => unreachable!()
        }
    }

    pub fn read32(&self, mut addr: u32) -> u32 {
        match (addr >> 24)&0xF {
            5 => {
                addr &= 0x3FF;
                bus_read_arr!(u32, self.palette_ram, addr as usize)
            },
            6 => {
                addr &= 0x17FFF;
                bus_read_arr!(u32, self.vram, addr as usize)
            },
            7 => {
                addr &= 0x3FF;
                bus_read_arr!(u32, self.obj_attrib, addr as usize)
            },
            _ => unreachable!()
        }
    }

    pub fn write16(&mut self, mut addr: u32, val: u16) {
        match (addr >> 24)&0xF {
            5 => {
                addr &= 0x3FF;
                bus_write_arr!(u16, self.palette_ram, addr as usize, val);
            },
            6 => {
                addr &= 0x17FFF;
                bus_write_arr!(u16, self.vram, addr as usize, val);
            },
            7 => {
                addr &= 0x3FF;
                bus_write_arr!(u16, self.obj_attrib, addr as usize, val);
            },
            _ => unreachable!()
        }
    }

    pub fn write32(&mut self, mut addr: u32, val: u32) {
        match (addr >> 24)&0xF {
            5 => {
                addr &= 0x3FF;
                bus_write_arr!(u32, self.palette_ram, addr as usize, val);
            },
            6 => {
                addr &= 0x17FFF;
                bus_write_arr!(u32, self.vram, addr as usize, val);
            },
            7 => {
                addr &= 0x3FF;
                bus_write_arr!(u32, self.obj_attrib, addr as usize, val);
            },
            _ => unreachable!()
        }
    }

    fn get_bg_color_from_palette(&self, color: u8) -> [u8; 3] {
        let c = {
            let index = color as usize*2;
            self.palette_ram[index] as u16 | ((self.palette_ram[index + 1] as u16) << 8)
        };
        u16_to_color(c)
    }

    pub fn tick(&mut self) {
        use PPU_state::*;

        match &mut self.state {
            HDRAW {cycles_left} => {
                if *cycles_left > 0 {
                    *cycles_left -= 1;
                } else {
                    let pixel_off = self.vcount.ly as usize*240;
                    for x in pixel_off .. pixel_off+240 {
                        let c = self.get_bg_color_from_palette(self.vram[x as usize]);
                        let index = x*3;
                        self.draw.buffer[index] = c[0];
                        self.draw.buffer[index+1] = c[1];
                        self.draw.buffer[index+2] = c[2];
                    }

                    self.dispstat.set(DisplayStatFlag::HBLANK);
                    self.state = PPU_state::new_hblank();
                }
            },
            HBLANK {cycles_left}   => {
                if *cycles_left > 0 {
                    *cycles_left -= 1;
                } else {
                    self.vcount.ly += 1;
                    if self.vcount.ly == VDRAW_HEIGHT {
                        self.dispstat.unset(DisplayStatFlag::HBLANK);
                        self.dispstat.set(DisplayStatFlag::VBLANK);
                        self.state = PPU_state::new_vblank();
                    } else {
                        self.dispstat.unset(DisplayStatFlag::HBLANK);
                        self.state = PPU_state::new_hdraw();
                    }
                }
            },
            VBLANK {cycles_left}  => {
                if *cycles_left > 0 {
                    *cycles_left -= 1;
                } else {
                    self.vcount.ly += 1;
                    if self.vcount.ly == VDRAW_HEIGHT+VBLANK_HEIGHT {
                        self.draw.new_frame();
                        self.vcount.ly = 0;
                        self.dispstat.unset(DisplayStatFlag::VBLANK);
                        self.state = PPU_state::new_hdraw();
                    } else {
                        *cycles_left = SCANLINE_CYCLES;
                    }
                }
            }
        }
    }
}
