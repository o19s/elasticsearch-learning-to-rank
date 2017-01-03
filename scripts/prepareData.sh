#!/bin/bash
wget -O ml-20m.zip http://files.grouplens.org/datasets/movielens/ml-20m.zip
unzip ml-20m.zip
sort -t , -nk1 < "ml-20m/ratings.csv" > ml-20m/ratings-sorted.csv
