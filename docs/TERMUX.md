# Termux workflow

Use Termux for Git and file management. Build in GitHub Codespaces: Gradle + Loom needs several GB of RAM and downloads
the Minecraft client, which is unreliable on most phones.

```bash
pkg update && pkg upgrade
pkg install git openssh openjdk-21        # JDK 21 if your Termux repo has it
java -version                              # must say 21 for local builds

git clone https://github.com/<you>/RKB-CLIENT.git
cd RKB-CLIENT
git pull                                   # get latest changes

# local build attempt (optional, may run out of memory):
bash scripts/bootstrap-wrapper.sh          # needs Gradle installed once
./gradlew build
ls build/libs/                             # rkbclient-0.1.1.jar is the one to install

git add -A
git commit -m "Describe your change"
git push
```

Download the finished JAR from the GitHub Actions run (Actions > latest run > Artifacts > rkbclient-jar) or from
Codespaces (`build/libs/`).
