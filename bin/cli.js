#!/usr/bin/env node
import { main } from "../lib/cli.js"
main.apply(null, process.argv.slice(2))
