#!/usr/bin/env python3
# -*- coding: utf-8 -*-
#
# This script lists all accounts and the number of transactions for each of
# them. The accounts to query are defined by a JSON file which contains
# username, password and consumer key.
#
# Format of input JSON file:
# [
#    {
#        "username": "",
#        "password": "",
#        "consumer_key": "",
#    }
# ]
#
# Example call: ./list_account_data.py logins.json


import json
import sys
from pprint import pprint

from lib import API, APIError, log
from settings import BASE_URL, API_VERSION


class ListAccountDataError(Exception):
    """Exception class for ListAccountData"""
    pass


class ListAccountData(object):
    def __init__(self, args):
        if len(args) < 2:
            msg = 'Usage: {} <json file>'.format(args[0])
            raise ListAccountDataError(msg)
        self.filename_logins = args[1]

    def print_transactions(self, api, account):
        urlpath = '/banks/{}/accounts/{}/{}/transactions'.format(
            account['bank_id'], account['id'], 'owner')
        transactions = api.get(urlpath)
        if 'transactions' in transactions:
            transactions = transactions['transactions']
        print('Number of transactions: {}'.format(len(transactions)))

    def print_accounts(self, username, api):
        print('')
        print('------')
        print('Accounts for username {} at {}:'.format(username, api.base_url))
        accounts = api.get('/my/accounts')
        for account in accounts:
            pprint(account)
            self.print_transactions(api, account)
            print('---')
        print('')

    def run(self):
        logins = json.loads(open(self.filename_logins).read())
        for login in logins:
            api = API(BASE_URL, API_VERSION)
            try:
                api.login(login['username'],
                          login['password'],
                          login['consumer_key'])
                self.print_accounts(login['username'], api)
            except APIError as err:
                log(str(err))


if __name__ == '__main__':
    ListAccountData(sys.argv).run()
