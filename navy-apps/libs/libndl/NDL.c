#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/time.h>
#include <fcntl.h>//time val
#include <assert.h>

static int evtdev = -1;
static int fbdev = -1;
static int screen_w = 0, screen_h = 0;

static struct timeval  tv;
uint32_t NDL_GetTicks() {
  struct timeval tv;
  gettimeofday(&tv, NULL);

  return tv.tv_usec / 1000 + tv.tv_sec * 1000;
}

int NDL_PollEvent(char *buf, int len) {
  int fp = open("/dev/events", O_RDONLY);//read only
  return read(fp, buf, sizeof(char) * len);
}

static int canvas_w, canvas_h, canvas_x = 0, canvas_y = 0;

void NDL_OpenCanvas(int *w, int *h) {
  if (*w == 0){
    *w = screen_w;
  }else if(*w > screen_w){
    assert(0);
  }
  if (*h == 0){
    *h = screen_h;
  }else if(*h > screen_h){
    assert(0);
  }
  canvas_w = *w;
  canvas_h = *h;
  if (getenv("NWM_APP")) {
    int fbctl = 4;
    fbdev = 5;
    screen_w = *w; screen_h = *h;
    char buf[64];
    int len = sprintf(buf, "%d %d", screen_w, screen_h);
    // let NWM resize the window and create the frame buffer
    write(fbctl, buf, len);
    while (1) {
      // 3 = evtdev
      int nread = read(3, buf, sizeof(buf) - 1);
      if (nread <= 0) continue;
      buf[nread] = '\0';
      if (strcmp(buf, "mmap ok") == 0) break;
    }
    close(fbctl);
  }
}

void NDL_DrawRect(uint32_t *pixels, int x, int y, int w, int h) {
  int graphics = open("/dev/fb", O_RDWR);
  
  for (int i = 0; i < h; ++i){// ((canvas_y + y + i) * screen_w + (canvas_x + x)) * sizeof(uint32_t)
    //printf("NDL:       offset%d\n",((canvas_y + y + i) * screen_w + (canvas_x + x)) * sizeof(uint32_t));
    // lseek(graphics, ((canvas_y + y + i) * screen_w + (canvas_x + x)) * sizeof(uint32_t), SEEK_SET);
    ssize_t s = write(graphics, pixels + w * i, w * sizeof(uint32_t));
  }
}

void NDL_OpenAudio(int freq, int channels, int samples) {
}

void NDL_CloseAudio() {
}

int NDL_PlayAudio(void *buf, int len) {
  return 0;
}

int NDL_QueryAudio() {
  return 0;
}

int NDL_Init(uint32_t flags) {
  if (getenv("NWM_APP")) {
    evtdev = 3;
  }

  //memset(info, 0, 128);
  uintptr_t dsize[2];
  int dinfo = open("/proc/dispinfo", 0);
  read(dinfo, &dsize, sizeof(dsize));
  close(dinfo);
  screen_w = dsize[0];
  screen_h = dsize[1];
  printf("With width = %d, height = %d.\n", screen_w, screen_h);

  return 0;
}

void NDL_Quit() {
}
