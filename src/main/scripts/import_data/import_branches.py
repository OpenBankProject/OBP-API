#!/usr/bin/env python3
# -*- coding: utf-8 -*-


import sys

from lib import ImportCSV


class ImportBranches(ImportCSV):
    method = 'POST'  # inconsistency in the API

    def get_urlpath(self):
        urlpath = '/banks/{}/branches'.format(self.bank_id)
        return urlpath

    def get_data(self, row_number, row):
        data = {
            'id': row_number,
            'bank_id': self.bank_id,
            'name': row[1],
            'address': {
                'line_1': row[1],
                'line_2': '',
                'line_3': '',
                'city': row[4],
                'state': '',
                'postcode': '',
                'country': '',
            },
            'location': {
                'latitude': 0,
                'longitude': 0,
            },
            'meta': {
                'license': {
                    'id': 'copyright',
                    'name': 'Copyright 2017',
                }
            },
            'lobby': {
                'hours': '0',
            },
            'drive_up': {
                'hours': '0',
            },
            'branch_routing': {
                'scheme': 'String',
                'address': 'String',
            }
        }
        return data


if __name__ == '__main__':
    importer = ImportBranches(sys.argv)
    importer.run()
