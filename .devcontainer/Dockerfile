FROM mcr.microsoft.com/devcontainers/base:debian-13

# Install git, gh, Chromium, and other dependencies
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends \
    git \
    gh \
    openssh-client \
    iptables \
    curl \
    ca-certificates \
    gpg \
    gnupg \
    build-essential \
    libssl-dev \
    libyaml-dev \
    libreadline6-dev \
    zlib1g-dev \
    libncurses5-dev \
    libffi-dev \
    libgdbm6 \
    libgdbm-dev \
    dbus \
    kmod \
    chromium \
    chromium-driver \
    wget

# Install mise
RUN install -dm 755 /etc/apt/keyrings \
    && wget -qO - https://mise.jdx.dev/gpg-key.pub | gpg --dearmor -o /etc/apt/keyrings/mise-archive-keyring.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/mise-archive-keyring.gpg arch=amd64] https://mise.jdx.dev/deb stable main" | tee /etc/apt/sources.list.d/mise.list \
    && apt-get update \
    && apt-get install -y mise

USER vscode
ENV MISE_DATA_DIR=/home/vscode/.local/share/mise
ENV MISE_CONFIG_DIR=/home/vscode/.config/mise
ENV PATH="/home/vscode/.local/bin:/home/vscode/.devcontainers/bin:/home/vscode/.local/share/mise/shims:${PATH}"

# DevPod reports anonymous usage statistics unless told not to. The firewall
# blocks that endpoint anyway, so turn it off rather than have every invocation
# wait on a rejected connection.
ENV DEVPOD_DISABLE_TELEMETRY=true

# Install tools
# Dev Container CLI is installed with the standalone script, which bundles its
# own Node.js runtime (https://github.com/devcontainers/cli#install-script)
RUN curl -fsSL https://raw.githubusercontent.com/devcontainers/cli/main/scripts/install.sh | sh
# DevPod CLI runs the same devcontainer.json on a pluggable backend. It ships as
# a single static binary (https://devpod.sh/docs/getting-started/install), and
# the built-in "docker" provider is registered here so that it targets the local
# Docker daemon provided by the docker-in-docker feature. That provider is
# embedded in the binary, so adding it needs no network access.
#
# Git credential and ssh signature forwarding are turned off because both work
# by rewriting ~/.gitconfig inside the workspace. Here that file is a symlink
# into /dev_container_home (see post_start_command.d/10-dev-container-home), so
# DevPod would edit the shared configuration on the host and leave behind a
# credential helper and a gpg.ssh.program pointing at paths that only exist
# inside the workspace, breaking git outside of it.
#
# The default IDE is set to "none" because DevPod otherwise falls back to
# opening VS Code, and there is none to open in here. "devpod up" then just
# starts the workspace, and "devpod ssh" is how you enter it.
#
# The binary is downloaded straight to its final location: DevPod itself uses
# /tmp/devpod as a working directory, so staging the download there would make
# every later "mkdir /tmp/devpod" fail with "not a directory".
RUN arch="$(dpkg --print-architecture)" \
    && install -d -m 0755 /home/vscode/.local/bin \
    && curl -fsSL -o /home/vscode/.local/bin/devpod \
      "https://github.com/loft-sh/devpod/releases/latest/download/devpod-linux-${arch}" \
    && chmod 0755 /home/vscode/.local/bin/devpod \
    && devpod provider add docker \
    && devpod context set-options \
      -o SSH_INJECT_GIT_CREDENTIALS=false \
      -o GIT_SSH_SIGNATURE_FORWARDING=false \
    && devpod ide use none
# Install Claude Code via the native installer (https://code.claude.com/docs/ja/quickstart)
RUN curl -fsSL https://claude.ai/install.sh | bash
# Antigravity CLI is distributed as a native binary (installed to ~/.local/bin/agy), not via npm
RUN curl -fsSL https://antigravity.google/cli/install.sh | bash
RUN mise use -g ruby@latest
# Node.js is not required anymore: the Dev Container CLI standalone installer
# bundles its own runtime. Uncomment if your project needs Node.js and npm.
# RUN mise use -g node@24

# Setup shell for mise
RUN echo 'eval "$(mise activate bash)"' >> ~/.bashrc \
    && echo 'eval "$(mise activate zsh)"' >> ~/.zshrc
