FROM openjdk:8u212-b04-jdk-slim-stretch

RUN mkdir -p /home/ci

# Create an app user so our program doesn't run as root.
RUN groupadd -r ci &&\
    useradd -r -g ci -d /home/ci -s /sbin/nologin -c "Docker image user" ci

# Set the home directory to our app user's home.
ENV HOME=/home/ci

RUN apt-get update \
    && apt-get install apt-transport-https dirmngr gnupg ca-certificates -y \
    && apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 3FA7E0328081BFF6A14DA29AA6A19B38D3D831EF \
    && echo "deb https://download.mono-project.com/repo/debian stable-stretch main" | tee /etc/apt/sources.list.d/mono-official-stable.list \
    && apt-get update \
    && apt-get install mono-devel -y \
    && apt-get clean

RUN chown -R ci:ci $HOME
RUN chmod -R 777 $HOME