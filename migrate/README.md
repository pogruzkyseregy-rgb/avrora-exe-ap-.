# Переезд AVRORA.EXE

Две машины:
- **основная** (сейчас `92.118.112.46`): сайт avrora-exe.ru, админка сайта, сервер девчонок (`/opt/aurora-relay`) —
  память Гесперы и Эст, задачи, табу, разговоры вдвоём, ядро Авроры, картинки и треки, сертификаты, ключ подписи приложения;
- **машина руки** (сейчас `37.220.86.132`, 4VPS): папки девчонок `~/work`, `~/hespera`, `~/est`, сервер Zero Spin с базой и ключами.

Копии содержат ключи API и токены. Храни их у себя (Telegram «Избранное», Google Диск), **не в GitHub** — репозиторий публичный.

## 0. Свежие копии (делать перед переездом, и вообще время от времени)

С телефона, из папки, где лежит распакованный архив проекта (`aurora-exe-vNN.zip`):

```
ssh root@92.118.112.46 "bash -s" < migrate/backup-main.sh
scp 'root@92.118.112.46:/root/avrora-main-*.tar.gz' ~/storage/downloads/

ssh root@37.220.86.132 "bash -s" < migrate/backup-hand.sh
scp 'root@37.220.86.132:/root/avrora-hand-*.tar.gz' ~/storage/downloads/
```

## 1. Новый сервер

Нужен Ubuntu 22.04 или 24.04, 1–2 ГБ памяти. Сначала положи туда свой ключ (в Termux `ssh-copy-id` сломан):

```
cat ~/.ssh/id_ed25519.pub | ssh root@НОВЫЙ 'mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && echo добавлен'
```

Потом залей архив проекта и копии:

```
scp ~/storage/downloads/aurora-exe-vNN.zip ~/storage/downloads/avrora-*.tar.gz root@НОВЫЙ:/root/
ssh root@НОВЫЙ 'apt-get install -y unzip && cd /root && unzip -oq aurora-exe-vNN.zip -d avrora'
```

## 2. Варианты

**А. Уходит основной, рука остаётся** — всё переносится на новую машину (или на машину руки, см. В):

```
ssh root@НОВЫЙ 'bash /root/avrora/migrate/restore-main.sh /root/avrora-main-….tar.gz'
```
Потом в SpaceWeb поменяй A-запись avrora-exe.ru и www на новый адрес. Приложение менять не нужно.

**Б. Уходит рука, основной остаётся:**

```
ssh root@НОВЫЙ 'cd /root/avrora && bash migrate/restore-hand.sh /root/avrora-hand-….tar.gz'
ssh root@92.118.112.46 'bash -s НОВЫЙ_IP' < migrate/set-hand.sh
```
В приложении мессенджера Zero Spin впиши новый адрес сервера (его печатает `restore-hand.sh`).

**В. Всё на одну машину** (дешевле всего):

```
ssh root@ОДНА 'bash /root/avrora/migrate/restore-main.sh /root/avrora-main-….tar.gz'
ssh root@ОДНА 'cd /root/avrora && COMBINED=1 bash migrate/restore-hand.sh /root/avrora-hand-….tar.gz'
ssh root@ОДНА 'bash -s 127.0.0.1' < migrate/set-hand.sh
```
Важно: на общей машине у Авроры будет sudo, а значит доступ и к ключам основного сервера. Если не хочешь —
добавь `WIDE=0` перед `bash migrate/restore-hand.sh` и убери строку `HAND_SUDO=1` из `/opt/aurora-relay/relay.env`.

## 3. Проверка

```
curl -s https://avrora-exe.ru/app-api/health; echo
```
Должно быть `"ok":true`. Дальше — как раньше.
