# Import Data

A collection of (Python) scripts to import data into the API using DirectLogin



# Setup

- In case you do not have `requests` installed, setup a virtual environment:

`virtualenv --python=python3 venv`

- Then activate it

`source venv/bin/activate`

- Now install requests in the virtualenv:

`pip install -r requirements.txt`

- For future runs of the scripts, you need to activate the virtualenv again, but do not need to recreate the virtualenv or install the requirements.

- Edit `settings.py` before running any of the scripts!



# Scripts

- `./import_branches.py <csv file> <bank id>`
- `./import_products.py <csv file> <bank id>`
