This folder contains files demonstrating how to work with xgboost.

## xgboost.txt
The sample training data utilized in the main demo folder but in LibSVM format which is used by xgboost.

## featmap.txt
A configuration file telling xgboost the feature names that feature ordinals refer to.

## xgb.py
A bare-bones script for reading `xgboost.txt` and training a model for usage with the plugin.

## xgb-model.json
Included for convenience, this is the output of xgb.py, a JSON formatted model compatible with the LTR plugin.


# Notes
Although there are PyPI packages for xgboost, some users will find that installation fails in their local environments.  If this occurs the best workaround is to clone the xgboost repository and do a local build with the latest.

```
git clone --recursive https://github.com/dmlc/xgboost
cd xgboost
make -j4
cd python-package
python setup.py install
```

Note: If doing a global install you may need to run with root privileges, the above should work in a virtual environment.
