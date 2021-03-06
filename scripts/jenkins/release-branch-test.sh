#!/bin/bash
PROJECTS="BT|CCE|CCM|CDC|CDNG|CDP|CE|CI|CV|CVNG|DEL|DOC|DX|ER|OPS|PL|SEC|SWAT|GTM|ONP"
git log --remotes=origin/release/* --pretty=oneline --abbrev-commit | grep -iE "\[(${PROJECTS})-[0-9]+]:" -o | sort | uniq | tr '[:lower:]' '[:upper:]' > release.txt
git log --remotes=origin/[m]aster --pretty=oneline --abbrev-commit | grep -iE "\[(${PROJECTS})-[0-9]+]:" -o | sort | uniq | tr '[:lower:]' '[:upper:]' > master.txt
NOT_MERGED=`comm -23 release.txt master.txt | tr '\n' ' '`
if [ -z "$NOT_MERGED" ]
then
      echo "Hotfix changes are reflected in Master as well" > envvars
else
      echo NOT_MERGED="${NOT_MERGED}" > envvars
fi
