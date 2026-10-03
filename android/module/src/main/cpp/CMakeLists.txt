cmake_minimum_required(VERSION 3.22)
project(cyclone_cloak CXX)

add_library(cloak SHARED cloak.cpp)

find_library(log-lib log)
target_link_libraries(cloak ${log-lib})
target_compile_features(cloak PRIVATE cxx_std_17)
