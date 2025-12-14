Copilot said: # Run a quick local test of
Run a quick local test of OBP-API on a MiniOS Linux notebook

This document explains how to install required tools (Docker, Docker Compose, git, curl, jq), start a Postgres test database, run the repository development docker-compose, and perform two quick API smoke tests (root and banks). Follow the appropriate package-manager section for your MiniOS variant.

    Assumptions

        You have a minimal Linux (MiniOS) with network access.
        You have sudo privileges.
        Steps are written to work with a fresh environment; adapt if some tools are already installed.

1. Install prerequisites

Choose the block for your distro.
Debian / Ubuntu (apt)
bash

sudo apt update
sudo apt install -y ca-certificates curl gnupg lsb-release git jq

# Install Docker (convenience script)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Install Docker Compose (standalone)
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Apply docker group immediately (or log out/in)
newgrp docker

Fedora (dnf)
bash

sudo dnf install -y git curl jq
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Docker Compose binary
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
newgrp docker

Alpine (apk)
bash

sudo apk update
sudo apk add git curl jq docker py3-pip
sudo rc-update add docker boot
sudo service docker start
sudo usermod -aG docker $USER

# Install docker-compose via pip
sudo pip3 install docker-compose
newgrp docker

Arch (pacman)
bash

sudo pacman -Syu --noconfirm git curl jq docker docker-compose
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
newgrp docker

Verify Docker:
bash

docker version
docker run --rm hello-world

2. Clone the repository
bash

cd ~
git clone https://github.com/wanmad-br/OBP-API.git

3. Start a Postgres test container

We create a Postgres container with user obp, password f, database obp_mapped and expose it on port 5432.
bash

docker run -d --name obp-postgres \
  -e POSTGRES_USER=obp \
  -e POSTGRES_PASSWORD=f \
  -e POSTGRES_DB=obp_mapped \
  -p 5432:5432 \
  postgres:14

Check it's running:
bash

docker ps --filter name=obp-postgres
docker logs -f obp-postgres   # optional: view logs

(Optional) Connect to DB:
bash

docker exec -it obp-postgres psql -U obp -d obp_mapped

4. Configure OBP compose to use the DB and start the stack

The repository includes a development docker-compose in development/docker. The compose file expects to connect to the DB via host.docker.internal (the compose sets an extra_hosts mapping to host-gateway). Export the OBP DB URL before starting.
bash

cd ~/OBP-API/development/docker

export OBP_DB_URL='jdbc:postgresql://host.docker.internal:5432/obp_mapped?user=obp&password=f'

Start the development environment (build + run Redis + obp-api):
bash

docker-compose up --build

Notes:

    If your Docker uses the new plugin-based command, use docker compose up --build.
    Keep this terminal open to watch logs. Wait until the obp-api container shows that the server (Jetty) is listening on port 8080.

5. Quick API smoke tests

Open a new terminal (or background the compose) and run the two tests below.

    Root endpoint (API metadata — public endpoint)

bash

curl -i http://localhost:8080/obp/v3.1.0/root

If you get 404, try other supported versions such as /obp/v3.0.0/root or /obp/v2.2.0/root.

    List banks (public endpoint)

bash

curl -sS http://localhost:8080/obp/v3.1.0/banks | jq .

If jq is not installed, omit the | jq . part:
bash

curl -sS http://localhost:8080/obp/v3.1.0/banks

Expected result: HTTP 200 and JSON describing the API (root) and a list (possibly empty) of banks.
6. Troubleshooting

    If the obp-api cannot connect to Postgres:
        Ensure obp-postgres container is running and port 5432 is reachable from the host.
        Confirm OBP_DB_URL was exported before starting compose.
        On some Linux hosts, host.docker.internal may not work. If you see host resolution errors, either:
            Replace host.docker.internal with the host IP in OBP_DB_URL, e.g. jdbc:postgresql://<HOST_IP>:5432/..., or
            Edit development/docker/docker-compose.yml to point the obp-api service DB settings to a DB service in the same compose, or
            Start Postgres inside the same compose (requires editing the compose file).

    Check logs:

bash

docker logs -f obp-api-app
docker logs -f obp-api-redis
docker logs -f obp-postgres

    If the app performs schema creation/migrations on startup, wait until those steps are complete (watch the logs for progress).

7. Stop and cleanup

Stop the compose stack:
bash

# from development/docker
docker-compose down
# or, if using plugin:
# docker compose down

Stop and remove the Postgres test container:
bash

docker stop obp-postgres
docker rm obp-postgres

Remove images/volumes if you want a full cleanup (optional).
8. Optional alternatives / tips

    To avoid host.docker.internal issues, you can create a docker-compose override that includes a Postgres service so everything runs in the same compose network.
    If you want a single copy-paste automation script (Debian/Ubuntu style) that:
        installs Docker & docker-compose,
        clones the repo,
        starts Postgres and the compose,
        runs the curl tests — I can produce that script for you.
