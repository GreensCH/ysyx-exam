#include "sdb.h"

#define NR_WP 32

typedef struct watchpoint {
  int NO;
  struct watchpoint *next;

  /* TODO: Add more members if necessary */
  vaddr_t pc;
  word_t val_old;
} WP;

static WP wp_pool[NR_WP] = {};
static WP *head = NULL, *free_ = NULL;

void init_wp_pool() {
  int i;
  for (i = 0; i < NR_WP; i ++) {
    wp_pool[i].NO = i;
    wp_pool[i].next = (i == NR_WP - 1 ? NULL : &wp_pool[i + 1]);
  }

  head = NULL;
  free_ = wp_pool;
}

/* TODO: Implement the functionality of watchpoint */

/* 
* new_wp(),free_wp()监视点池的接口，被其它函数调用
* 
*/

/*
* new_wp()从free_链表中返回一个空闲的监视点结构
*/
WP* new_wp(){
  if(free_ == NULL)
    Assert(0, "*** ERROR Watch-Pool Overflow***");
  else{
    WP *old_head_wp = head;
    head = free_;// head forward 1
    free_ = free_ -> next;// free back 1
    head -> next = old_head_wp;//change add point's next
    return head;
  }
}

void clear_wp_info(WP *wp);

/*
* free_wp()将wp归还到free_链表中
*/
void free_wp(WP *wp){
  WP *old_p = NULL;
  for(WP *p = head; p -> next != NULL; p = p->next){
    if(p -> NO == wp -> NO){
      old_p -> next = p ->next;
      p -> next = free_;
      free_ = p;
      clear_wp_info(p);
      break;
    }
    old_p = p;
  }
}

void new_wp_expr(char *args){
  bool success = false;
  /* word_t val = */expr(args, &success);
  WP* p = new_wp();

  printf("(head)id:");
  if(head!=NULL) printf("%4d,next:",head->NO);
  else printf("NULL,next:");
  if(head->next!=NULL) printf("%4d,\n",head->next->NO);
  else printf("NULL,\n");

  printf("(free)id:");
  if(free_!=NULL) printf("%4d,next:",free_->NO);
  else printf("NULL,next:");
  if(free_->next!=NULL) printf("%4d,\n",free_->next->NO);
  else printf("NULL,\n");

  printf("(addp)id:");
  if(p!=NULL) printf("%4d,next:",p->NO);
  else printf("NULL,next:");
  if(p->next!=NULL) printf("%4d,\n",p->next->NO);
  else printf("NULL,\n");
}