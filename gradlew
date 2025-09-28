#!/bin/sh

if [ -n "${ZSH_VERSION+x}" ]; then
    setopt sh_word_split
fi

if [ -z "${GRADLE_HOME+x}" ]; then
    GRADLE_HOME="$HOME/.gradle"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"