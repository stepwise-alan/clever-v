typedef signed char smallint;
typedef unsigned long int __dev_t;
typedef unsigned int __uid_t;
typedef unsigned int __gid_t;
typedef unsigned long int __ino_t;
typedef unsigned int __mode_t;
typedef unsigned long int __nlink_t;
typedef long int __off_t;
typedef long int __time_t;
typedef long int __blksize_t;
typedef long int __blkcnt_t;
typedef __off_t off_t;
typedef __time_t time_t;

typedef unsigned long long uoff_t_0;

typedef unsigned long uoff_t_1;

enum  {
  TERMINAL_WIDTH = 80,
  COLUMN_GAP = 2,
  STYLE_COLUMNS = (1 << 21),
  STYLE_LONG = (2 << 21),
  STYLE_SINGLE = (3 << 21),
  STYLE_MASK = STYLE_SINGLE,
  LIST_INO = (1 << 0),
  LIST_BLOCKS = (1 << 1),
  LIST_MODEBITS = (1 << 2),
  LIST_NLINKS = (1 << 3),
  LIST_ID_NAME = (1 << 4),
  LIST_ID_NUMERIC = (1 << 5),
  LIST_CONTEXT = (1 << 6),
  LIST_SIZE = (1 << 7),
  LIST_DATE_TIME = (1 << 9),
  LIST_FULLTIME = (1 << 10),
  LIST_FILENAME = (1 << 11),
  LIST_SYMLINK = (1 << 12),
  LIST_FILETYPE = (1 << 13),
  LIST_EXEC = (1 << 14),
  LIST_MASK = ((LIST_EXEC << 1) - 1),
  DISP_DIRNAME = (1 << 15),
  DISP_HIDDEN = (1 << 16),
  DISP_DOT = (1 << 17),
  DISP_NOLIST = (1 << 18),
  DISP_RECURSIVE = (1 << 19),
  DISP_ROWS = (1 << 20),
  DISP_MASK = (((DISP_ROWS << 1) - 1) & (~ (DISP_DIRNAME - 1))),
  SORT_FORWARD = 0,
  SORT_REVERSE = (1 << 27),
  SORT_NAME = 0,
  SORT_SIZE = (1 << 28),
  SORT_ATIME = (2 << 28),
  SORT_CTIME = (3 << 28),
  SORT_MTIME = (4 << 28),
  SORT_VERSION = (5 << 28),
  SORT_EXT = (6 << 28),
  SORT_DIR = (7 << 28),
  SORT_MASK = (7 << 28),
  LIST_SHORT = LIST_FILENAME,
  LIST_LONG = (LIST_MODEBITS | LIST_NLINKS | LIST_ID_NAME | LIST_SIZE | LIST_DATE_TIME | LIST_FILENAME | LIST_SYMLINK),
  SPLIT_DIR = 1,
  SPLIT_FILE = 0,
  SPLIT_SUBDIR = 2
} ;

static  int sortcmp(__off_t st_size1 , __off_t st_size2 , __time_t st_atim_tv_sec1 , __time_t st_atim_tv_sec2 , __time_t st_ctim_tv_sec1 , __time_t st_ctim_tv_sec2, __time_t st_mtim_tv_sec1 , __time_t st_mtim_tv_sec2 , __mode_t st_mode1 , __mode_t st_mode2 , int strcoll12 , int strcmp12 , unsigned all_fmt , _Bool sort_size, _Bool sort_atime, _Bool sort_ctime, _Bool sort_mtime, _Bool sort_dir, _Bool sort_reverse ) {
  off_t dif = 0;
  if (sort_size) {
    (dif = (st_size2 - st_size1));
  }
  else if (sort_atime) {
    (dif = (st_atim_tv_sec2 - st_atim_tv_sec1));
  }

  else if (sort_ctime) {
    (dif = (st_ctim_tv_sec2 - st_ctim_tv_sec1));
  }

  else if (sort_mtime) {
    (dif = (st_mtim_tv_sec2 - st_mtim_tv_sec1));
  }

  else if (sort_dir) {
    (dif = (((st_mode2 & 0170000) == 0040000) - ((st_mode1 & 0170000) == 0040000)));
  }
  if ((dif == 0)) {
    #ifdef CONFIG_LOCALE_SUPPORT
    (dif = strcoll12);
    #else
    (dif = strcmp12);
    #endif
  }
  if ((sizeof(off_t) > sizeof(int ))) {
    if ((dif != 0)) {
      #ifdef CONFIG_LFS
      (dif = ((int ) (((uoff_t_0 ) dif) >> (8 * (sizeof(dif) - sizeof(int ))))));
      dif += dif % 2;
      #else
      (dif = ((int ) (((uoff_t_1 ) dif) >> (8 * (sizeof(dif) - sizeof(int ))))));
      dif += dif % 2;
      #endif
    }
  }
  return (sort_reverse ? (- ((int ) dif)) : ((int ) dif));
}
