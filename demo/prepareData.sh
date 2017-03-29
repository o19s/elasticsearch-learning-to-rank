#!/bin/bash
wget -O RankLib.jar http://es-learn-to-rank.labs.o19s.com/RankLib-2.8.jar
wget -O ml-20m.zip http://files.grouplens.org/datasets/movielens/ml-20m.zip
unzip ml-20m.zip
sort -t , -nk1 < "ml-20m/ratings.csv" > ml-20m/ratings-sorted.csv
python tmdb.py
