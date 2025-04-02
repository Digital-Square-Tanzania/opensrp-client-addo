#!/bin/bash
assets="app/src/main/assets"
meta="${assets}/json_forms_modified_date.json"


fill_in_meta (){
    locale="${1:-json.form}"
    directory="$assets/$locale"

    [ -d "$directory" ] || { echo "Error: Directory '$directory' not found."; exit 1; }

    for file in "$directory"/*.json; do
        [ -f "$file" ] || continue;
        fname="$locale/$(basename $file)"
        modified_date=$(stat -c %y "$file" | cut -d'.' -f1)
        cat <<-doc >> "$meta"
    {
       "form": "$fname",
       "modifiedDate": "$modified_date"
    },
doc
        done
}


generate(){
    echo "[" >"$meta"
    for f in "$assets/json.form"*; do fill_in_meta "${f##*/}"; done
    echo "]" >> "$meta"
    sed -i -z 's/, *\n]/\n]/' $meta
}

generate

