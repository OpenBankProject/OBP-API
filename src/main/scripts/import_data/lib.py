# -*- coding: utf-8 -*-

import csv
import requests
import sys

from settings import (
    DEBUG, BASE_URL, API_VERSION, USERNAME, PASSWORD, CONSUMER_KEY)


def log(msg):
    out = sys.stdout.buffer  # else issues with utf-8 encoding using print()!
    out.write(msg.encode('utf-8'))
    out.write(b'\n')


def debug(msg):
    if DEBUG:
        log('DEBUG: {}'.format(msg))


def error(msg):
    log('ERROR: {}'.format(msg))
    sys.exit(1)


class APIError(Exception):
    """Exception class for API errors"""
    pass


class API(object):
    """OBP API Helper class"""
    def __init__(self, base_url, api_version):
        self.base_url = base_url
        self.api_url = '{}/obp/{}'.format(base_url, api_version)
        self.token = None

    def login(self, username, password, consumer_key):
        """Login to the API"""
        url = '{}/my/logins/direct'.format(self.base_url)
        fmt = 'DirectLogin username="{}",password="{}",consumer_key="{}"'
        headers = {
            'Authorization': fmt.format(username, password, consumer_key)
        }
        debug('Login as {0} to {1}'.format(headers, url))
        response = requests.post(url, headers=headers)
        if (response.status_code != 200):
            raise APIError('Could not login: {}'.format(response.text))
        self.token = response.json()['token']
        debug('Received token: {0}'.format(self.token))
        return self.token

    def handle_response(self, response):
        """Handle the response, e.g. errors or conversion to JSON"""
        if response.status_code in [404, 500]:
            msg = '{}: {}'.format(response.status_code, response.text)
            raise APIError(msg)
        elif response.status_code in [204]:
            return response.text
        else:
            data = response.json()
            if 'error' in data:
                raise APIError(data['error'])
            return data

    def call(self, method='GET', urlpath='', data=None):
        """Actual call to the API"""
        url = '{}{}'.format(self.api_url, urlpath)
        headers = {
            'Authorization': 'DirectLogin token={}'.format(self.token),
            'Content-Type': 'application/json',
        }
        response = requests.request(method, url, headers=headers, json=data)
        return self.handle_response(response)

    def get(self, urlpath=''):
        return self.call('GET', urlpath)

    def delete(self, urlpath):
        return self.call('DELETE', urlpath)

    def post(self, urlpath, data):
        return self.call('POST', urlpath, data)

    def put(self, urlpath, data):
        return self.call('PUT', urlpath, data)

    def get_current_user(self):
        """Convenience wrapper to get current user"""
        return self.get('/users/current')


class ImportCSVError(Exception):
    """Exception class for Importer errors"""
    pass


class ImportCSV(object):
    """
    Convert a CSV file into a format to be posted to the OBP API
    This is a base class for importer scripts to specialise
    """
    method = 'PUT'

    def __init__(self, args):
        if len(args) < 3:
            msg = 'Usage: {} <csv file> <bank id>'.format(args[0])
            raise ImportCSVError(msg)
        self.filename = args[1]
        self.bank_id = args[2]

    def run(self):
        """
        Do the actual work by reading the CSV file and posting data to API
        """
        api = API(BASE_URL, API_VERSION)
        if api.login(USERNAME, PASSWORD, CONSUMER_KEY):
            with open(self.filename, newline='', encoding='utf-8') as f:
                reader = csv.reader(f)
                next(reader, None)  # skip header
                row_number = 0
                for row in reader:
                    data = self.get_data(row_number, row)
                    debug('{} data: {}'.format(self.method, data))
                    api.call(self.method, self.get_urlpath(), data)
                    row_number += 1

    def get_urlpath(self, row):
        """
        Define URL path to POST to API
        This must be implemented by child class!
        """
        raise(NotImplementedError)

    def get_data(self, row_number, row):
        """
        Define data to POST to API
        This must be implemented by child class!
        """
        raise(NotImplementedError)
