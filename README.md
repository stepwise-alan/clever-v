# CLEVER V - Variability Aware Functional Equivalence Checker

```shell
$ docker build -t clever-v .
$ docker run -it clever-v "runMain EquivalenceChecker1 \
--old /home/clever_user/clever-v/examples/coreutils.ls.6b01b71e.unsat/old.c \
--new /home/clever_user/clever-v/examples/coreutils.ls.6b01b71e.unsat/new.c \
--function sortcmp \
--sea /opt/seahorn/build/run/bin/sea \
--z3 /opt/seahorn/build/run/bin/z3 \
--out /home/clever_user/clever-v/out \
-q \
--typechef-args \
-c,/home/clever_user/TypeChef-BusyboxAnalysis/ubuntu.properties,\
-U,HAVE_LIBDMALLOC,-DCONFIG_FIND,-U,CONFIG_FEATURE_WGET_LONG_OPTIONS,\
-U,ENABLE_NC_110_COMPAT,-U,CONFIG_EXTRA_COMPAT,-D_GNU_SOURCE,-x,CONFIG_,\
--include,/home/clever_user/TypeChef-BusyboxAnalysis/busybox/config.h,\
-I,/home/clever_user/TypeChef-BusyboxAnalysis/busybox-1.18.5/include,\
--featureModelFExpr,/home/clever_user/TypeChef-BusyboxAnalysis/busybox/featureModel,\
--parse"
```
