#include <am.h>
#include <nemu.h>

#define SYNC_ADDR (VGACTL_ADDR + 4)

#include <klib.h>
#define N 32
void __am_gpu_init() {
  int i;
  uint32_t vgactl = inl(VGACTL_ADDR);
  uint32_t w = vgactl >> 16;  // TODO: get the correct width
  uint32_t h = vgactl & 0xFFFF;  // TODO: get the correct height
  uint32_t *fb = (uint32_t *)(uintptr_t)FB_ADDR;
  for (i = 0; i < w * h; i ++) fb[i] = i;
  outl(SYNC_ADDR, 1);
  printf("%d %d\n", w, h);
}

void __am_gpu_config(AM_GPU_CONFIG_T *cfg) {
  uint32_t vgactl = inl(VGACTL_ADDR);
  uint32_t w = vgactl >> 16;  
  uint32_t h = vgactl & 0xFFFF;
  *cfg = (AM_GPU_CONFIG_T) {
    .present = true, .has_accel = false,
    .width = w, .height = h,
    .vmemsz = 0
  };
}

void __am_gpu_fbdraw(AM_GPU_FBDRAW_T *ctl) {

  uint32_t vgactl = inl(VGACTL_ADDR);
  uint32_t w = vgactl >> 16;  
  uint32_t *fb = (uint32_t *)(uintptr_t)FB_ADDR;
  printf("%d\n", w);
  int cnt=0;
  for(int j = 0; j < ctl->h ; j ++){
    for (int i = 0; i < ctl->w; i ++){
      int p = ctl->y + j;
      int q = ctl->x + i;
      fb[p * w + q] = ((uint32_t*)ctl->pixels)[++cnt];
    }
  }
  if (ctl->sync) {
    outl(SYNC_ADDR, 1);
  }
}

void __am_gpu_status(AM_GPU_STATUS_T *status) {
  status->ready = true;
}
