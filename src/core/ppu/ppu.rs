use raylib::prelude::*;

use crate::core::ppu::io::*;
use crate::core::io::MemoryMappedRegister;

const SCREEN_RECT: Rectangle = Rectangle {
    height: 160.,
    width: 240.,
    x: 0.,
    y: 0.
};

const WH_RATIO: f32 = 240./160.;

const HDRAW_CYCLES: u32 = 960;
const HBLANK_CYCLES: u32 = 272;
const SCANLINE_CYCLES: u32 = HDRAW_CYCLES + HBLANK_CYCLES;

const VBLANK_HEIGHT: u16 = 68;
const VDRAW_HEIGHT: u16 = 160;

struct TextTile {
    tile: usize,
    horizontal_flip: bool,
    vertical_flip: bool,
    palette: u8
}

impl TextTile {
    #[inline(always)]
    pub fn new(tile_data: u16) -> Self {
        Self {
            tile: tile_data as usize&0x3FF,
            horizontal_flip: tile_data&0x400 != 0,
            vertical_flip: tile_data&0x800 != 0,
            palette: ((tile_data >> 12)&0xF) as u8
        }
    }
}

#[inline(always)]
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


pub struct Draw {
    pub handle: RaylibHandle,
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
            screen_size: SCREEN_RECT
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
        draw.draw_texture_pro(&self.screen_texture, SCREEN_RECT, self.screen_size, self.screen_position, 0., Color::WHITE);
        draw.draw_fps(0, 0);
    }
}


pub struct PPU {
    pub draw: Draw,
    state: PPU_state,

    palette_ram: [u8; 1024],
    vram: [u8; 96*1024],
    obj_attrib: [u8; 1024],

    pub dispstat: DisplayStat,
    pub dispcnt: DisplayControl,
    pub vcount: VCount,
    pub bgcnt: [BackgroundControl; 4]
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
            vcount: VCount::new(),
            bgcnt: [BackgroundControl::new(); 4]
        }
    }

    pub fn read8(&self, mut addr: u32) -> u8 {
        match addr {
            0x5000000 ..= 0x5FFFFFF => {
                self.palette_ram[(addr&0x3FF) as usize]
            },
            0x6000000 ..= 0x6FFFFFF => {
                addr &= 0x1FFFF;

                if addr > 0x17FFF {
                    addr -= 0x8000;
                }
                
                self.vram[(addr&0x17FFF) as usize]
            },
            0x7000000 ..= 0x7FFFFFF => {
                self.obj_attrib[(addr&0x3FF) as usize]
            },
            _ => unreachable!()
        }
    }

    pub fn read16(&self, mut addr: u32) -> u16 {
        match addr {
            0x5000000 ..= 0x5FFFFFF => {
                addr &= 0x3FF;
                bus_read_arr!(u16, self.palette_ram, addr as usize)
            },
            0x6000000 ..= 0x6FFFFFF => {
                addr &= 0x1FFFF;

                if addr > 0x17FFF {
                    addr -= 0x8000;
                }
                
                bus_read_arr!(u16, self.vram, addr as usize)
            },
            0x7000000 ..= 0x7FFFFFF => {
                addr &= 0x3FF;
                bus_read_arr!(u16, self.obj_attrib, addr as usize)
            },
            _ => unreachable!()
        }
    }

    pub fn read32(&self, mut addr: u32) -> u32 {
        match addr {
            0x5000000 ..= 0x5FFFFFF => {
                addr &= 0x3FF;
                bus_read_arr!(u32, self.palette_ram, addr as usize)
            },
            0x6000000 ..= 0x6FFFFFF => {
                addr &= 0x1FFFF;

                if addr > 0x17FFF {
                    addr -= 0x8000;
                }
                
                bus_read_arr!(u32, self.vram, addr as usize)
            },
            0x7000000 ..= 0x7FFFFFF => {
                addr &= 0x3FF;
                bus_read_arr!(u32, self.obj_attrib, addr as usize)
            },
            _ => unreachable!()
        }
    }

    pub fn write16(&mut self, mut addr: u32, val: u16) {
        match addr {
            0x5000000 ..= 0x5FFFFFF => {
                addr &= 0x3FF;
                bus_write_arr!(u16, self.palette_ram, addr as usize, val);
            },
            0x6000000 ..= 0x6FFFFFF => {
                addr &= 0x1FFFF;

                if addr > 0x17FFF {
                    addr -= 0x8000;
                }

                bus_write_arr!(u16, self.vram, addr as usize, val);
            },
            0x7000000 ..= 0x7FFFFFF => {
                addr &= 0x3FF;
                bus_write_arr!(u16, self.obj_attrib, addr as usize, val);
            },
            _ => unreachable!()
        }
    }

    pub fn write32(&mut self, mut addr: u32, val: u32) {
        match addr {
            0x5000000 ..= 0x5FFFFFF => {
                addr &= 0x3FF;
                bus_write_arr!(u32, self.palette_ram, addr as usize, val);
            },
            0x6000000 ..= 0x6FFFFFF => {
                addr &= 0x1FFFF;

                if addr > 0x17FFF {
                    addr -= 0x8000;
                }
                
                bus_write_arr!(u32, self.vram, addr as usize, val);
            },
            0x7000000 ..= 0x7FFFFFF => {
                addr &= 0x3FF;
                bus_write_arr!(u32, self.obj_attrib, addr as usize, val);
            },
            _ => unreachable!()
        }
    }

    #[inline(always)]
    fn get_bg_color_from_palette(&self, color: u8) -> [u8; 3] {
        let c = {
            let index = color as usize*2;
            self.palette_ram[index] as u16 | ((self.palette_ram[index + 1] as u16) << 8)
        };
        u16_to_color(c)
    }

    fn render_bg_mode4(&mut self) {
        let pixel_off = self.vcount.ly as usize*240;

        for x in pixel_off .. pixel_off+240 {
            let c = self.get_bg_color_from_palette(self.vram[x as usize]);
            let index = x*3;
            self.draw.buffer[index] = c[0];
            self.draw.buffer[index+1] = c[1];
            self.draw.buffer[index+2] = c[2];
        }
    }

    fn render_bg_mode3(&mut self) {
        let pixel_off = self.vcount.ly as usize*480; // 240 * 2 cause 2 bytes

        for x in (pixel_off .. pixel_off + 480).step_by(2) {
            let color = u16_to_color(self.vram[x] as u16 | ((self.vram[x+1] as u16) << 8));

            let index = (x/2)*3;
            self.draw.buffer[index] = color[0];
            self.draw.buffer[index+1] = color[1];
            self.draw.buffer[index+2] = color[2];
        }
    }

    fn render_bg(&mut self, bg: usize) {
        let bg = self.bgcnt[bg];
        let mut tilemap_off = (self.vcount.ly as usize/8)*64 + bg.tile_map_offset;
        let y_tile_off = self.vcount.ly as usize%8;

        let mut buffer_off = self.vcount.ly as usize*240*3;
        for i in 0 .. 30 {
            let tile_data = TextTile::new(bus_read_arr!(u16, self.vram, tilemap_off));

            if bg.colors { // 256
                let tile_off = tile_data.tile*64 + bg.tile_data_offset + y_tile_off*8;

                for j in 0 .. 8 {
                    let c = self.get_bg_color_from_palette(self.vram[tile_off+j]);
                    self.draw.buffer[buffer_off] = c[0];
                    self.draw.buffer[buffer_off + 1] = c[1];
                    self.draw.buffer[buffer_off + 2] = c[2];
                    buffer_off += 3;
                }

            } else { // 16/16
                let palette_off = tile_data.palette*16;
                let tile_off = tile_data.tile*32 + bg.tile_data_offset + y_tile_off*4;

                for j in 0 .. 4 {
                    let pixels = {
                        let data = self.vram[tile_off + j];
                        ((data&0xF) + palette_off, (data >> 4) + palette_off)
                    };

                    let c = self.get_bg_color_from_palette(pixels.0);
                    self.draw.buffer[buffer_off] = c[0];
                    self.draw.buffer[buffer_off + 1] = c[1];
                    self.draw.buffer[buffer_off + 2] = c[2];

                    let c = self.get_bg_color_from_palette(pixels.1);
                    self.draw.buffer[buffer_off + 3] = c[0];
                    self.draw.buffer[buffer_off + 4] = c[1];
                    self.draw.buffer[buffer_off + 5] = c[2];

                    buffer_off += 6;
                }
            }

            tilemap_off += 2;
        }
    }

    fn render_bg_mode0(&mut self) {
        self.render_bg(0);
        // self.render_bg(1);
        // self.render_bg(2);
    }

    pub fn tick(&mut self) -> bool {
        use PPU_state::*;

        match &mut self.state {
            HDRAW {cycles_left} => {
                if *cycles_left > 0 {
                    *cycles_left -= 1;
                } else {
                    match self.dispcnt.bg_mode {
                        0 => self.render_bg_mode0(),
                        3 => self.render_bg_mode3(),
                        4 => self.render_bg_mode4(),
                        _ => self.render_bg_mode3() // println!("Unsupported bg mode: {}", self.dispcnt.bg_mode)
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
                    if self.vcount.ly >= VDRAW_HEIGHT {
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
                        return true;
                    } else {
                        *cycles_left = SCANLINE_CYCLES;
                    }
                }
            }
        }

        false
    }
}
