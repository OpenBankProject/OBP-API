#!/usr/bin/env python3
# -*- coding: utf-8 -*-


import sys

from lib import ImportCSV


class ImportProducts(ImportCSV):
    def get_urlpath(self):
        urlpath = '/banks/{}/products'.format(self.bank_id)
        return urlpath

    def get_data(self, row_number, row):
        data = {
            'bank_id': self.bank_id,
            'code': row[0],
            'name': row[1],
            'category': '',
            'family': '',
            'super_family': '',
            'more_info_url': '',
            'details':
                '{}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}'.format(  # noqa
                row[2], row[3], row[4], row[5], row[6], row[7], row[8], row[9],
                row[10], row[11], row[12], row[13], row[14], row[15], row[16],
                row[17], row[18], row[19], row[20], row[21], row[22], row[23],
                row[24]),
            'description': '',
            'meta': {
                'license': {
                    'id': 'copyright',
                    'name': 'Copyright 2017',
                }
            }
        }
        return data


if __name__ == '__main__':
    importer = ImportProducts(sys.argv)
    importer.run()
