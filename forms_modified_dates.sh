#!/bin/bash
assets="app/src/main/assets"
meta="${assets}/json_forms_modified_date.json"

[ -f "$meta" ] || echo "[]" > "$meta"

unindent(){
    indent_spaces=8
    sed -r "s/^\s{$indent_spaces}//g" 
}

update_meta_entry() {
    local form="$1"
    local modified_date="$2"

    unindent <<-PyCODE | python3 
        import json
        with open("$meta","r+") as file:
            data = json.load(file)
            found = next(filter(lambda x:x.get("form")=="$form",data),None)
            if found:
                found["modifiedDate"]= "$modifed_date"
            else:
                data.append({"form": "$form", "modifiedDate": "$modified_date"})
            file.seek(0); file.truncate(0)
            json.dump(data,file,indent=2)
PyCODE
}

process_locale() {
    local locale="${1:-json.form}"
    local directory="$assets/$locale"

    [ -d "$directory" ] || { echo "Error: Directory '$directory' not found."; exit 1; }

    git diff --name-only origin/online-forms -- "$directory" | while read -r file; do
        [ -f "$file" ] || { echo hapa inafika sasa na $directory na $file; continue; }
        local fname="${locale}/$(basename "$file")"
        local modified_date
        modified_date=$(stat -c %y "$file" | cut -d'.' -f1)
        update_meta_entry "$fname" "$modified_date"
    done
}

generate(){
    for d in "$assets/json.form"*; do
        process_locale "${d##*/}"
    done
}

generate
